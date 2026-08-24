package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.execution.ExecutionFailurePhase;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.RunnerSparkMode;
import cn.superhuang.data.scalpel.contract.quality.FormatPatternKind;
import cn.superhuang.data.scalpel.contract.quality.FormatPatternPreset;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityExecutionPayload;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleDefinition;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import cn.superhuang.data.scalpel.contract.quality.QualityConditionOperator;
import cn.superhuang.data.scalpel.contract.quality.QualityFieldComparisonOperator;
import cn.superhuang.data.scalpel.contract.quality.ViolationMetric;
import cn.superhuang.data.scalpel.contract.quality.ViolationTolerance;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.datascalpel.taskengine.contract.ModelQualityExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.QualityRuleExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.QualityRuleMetric;
import cn.superhuang.datascalpel.taskengine.contract.QualityRuleState;
import cn.superhuang.datascalpel.taskengine.contract.QualityRuleTechnicalFailure;
import cn.superhuang.datascalpel.taskengine.contract.QualitySampleResult;
import cn.superhuang.datascalpel.taskengine.contract.QualitySampleStatus;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionError;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionManifest;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionResult;
import cn.superhuang.datascalpel.taskengine.contract.TaskExecutionState;
import cn.superhuang.datascalpel.taskengine.spark.SedonaSparkSupport;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.storage.StorageLevel;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import static org.apache.spark.sql.functions.*;

/** Independent Spark execution path for model-quality rules; it never calls Canvas compilation. */
final class ModelQualityTaskExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ModelQualityTaskExecutor.class);
    private final RunnerFailureClassifier failureClassifier = new RunnerFailureClassifier();

    TaskExecutionResult execute(
            TaskExecutionManifest manifest,
            RunnerSparkMode sparkMode,
            Consumer<String> sparkStarted,
            cn.superhuang.data.scalpel.contract.execution.TaskExecutionLaunchDescriptor launch,
            Path workDirectory,
            RunnerArtifactAccess artifactAccess
    ) {
        validateManifest(manifest);
        Instant startedAt = Instant.now();
        SparkSession.Builder builder = SedonaSparkSupport.builder()
                .appName("DataScalpel Model Quality " + manifest.execution().executionId())
                .config("spark.ui.enabled", "false")
                .config("spark.sql.shuffle.partitions", "4")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.ansi.enabled", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .config("spark.speculation", "false");
        if (sparkMode == RunnerSparkMode.LOCAL) builder.master("local[*]");
        SparkSession spark = SedonaSparkSupport.initialize(builder.getOrCreate());
        try {
            sparkStarted.accept(spark.sparkContext().applicationId());
            QualitySampleArtifactWriter sampleWriter = new QualitySampleArtifactWriter(
                    manifest.modelQuality().failureSampleLimit(), launch, workDirectory, artifactAccess);
            return executeWithSpark(manifest, spark, startedAt, sampleWriter);
        } catch (Throwable throwable) {
            TaskExecutionError error = failureClassifier.classify(
                    throwable, RunnerFailureContext.task(ExecutionFailurePhase.PROCESS));
            LOGGER.error("event=TASK_FAILURE_DETAIL executionId={} code={} diagnosticId={}\n{}",
                    manifest.execution().executionId(), error.code(), error.diagnosticId(),
                    RunnerLogSanitizer.stackTrace(throwable));
            Instant endedAt = Instant.now();
            return new TaskExecutionResult(
                    TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                    manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                    failureState(error), startedAt, endedAt, Duration.between(startedAt, endedAt).toMillis(),
                    null, List.of(), ExecutionTaskType.SPARK_MODEL_QUALITY,
                    ModelQualityExecutionResult.technicalFailure(null, List.of(), List.of(), null), error);
        } finally {
            spark.stop();
        }
    }

    TaskExecutionResult execute(
            TaskExecutionManifest manifest,
            RunnerSparkMode sparkMode,
            Consumer<String> sparkStarted
    ) {
        if (manifest.modelQuality() != null && manifest.modelQuality().failureSampleLimit() > 0) {
            throw new RunnerExecutionException(
                    "QUALITY_SAMPLE_LAUNCH_REQUIRED", "启用失败样本的质检任务缺少启动制品信息", null);
        }
        validateManifest(manifest);
        Instant startedAt = Instant.now();
        SparkSession.Builder builder = SedonaSparkSupport.builder()
                .appName("DataScalpel Model Quality " + manifest.execution().executionId())
                .config("spark.ui.enabled", "false")
                .config("spark.sql.shuffle.partitions", "4")
                .config("spark.sql.caseSensitive", "true")
                .config("spark.sql.ansi.enabled", "true")
                .config("spark.sql.session.timeZone", "UTC")
                .config("spark.speculation", "false");
        if (sparkMode == RunnerSparkMode.LOCAL) builder.master("local[*]");
        SparkSession spark = SedonaSparkSupport.initialize(builder.getOrCreate());
        try {
            sparkStarted.accept(spark.sparkContext().applicationId());
            return executeWithSpark(manifest, spark, startedAt,
                    QualitySampleArtifactWriter.disabled(
                            Path.of(System.getProperty("java.io.tmpdir"), "data-scalpel-quality-disabled")));
        } catch (Throwable throwable) {
            TaskExecutionError error = failureClassifier.classify(
                    throwable, RunnerFailureContext.task(ExecutionFailurePhase.PROCESS));
            Instant endedAt = Instant.now();
            return new TaskExecutionResult(
                    TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                    manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                    failureState(error), startedAt, endedAt, Duration.between(startedAt, endedAt).toMillis(),
                    null, List.of(), ExecutionTaskType.SPARK_MODEL_QUALITY,
                    ModelQualityExecutionResult.technicalFailure(null, List.of(), List.of(), null), error);
        } finally {
            spark.stop();
        }
    }

    static TaskExecutionResult failure(
            UUID executionId,
            UUID runId,
            int attempt,
            Instant startedAt,
            Throwable throwable
    ) {
        TaskExecutionError error = new RunnerFailureClassifier().classify(
                throwable, RunnerFailureContext.task(ExecutionFailurePhase.PREPARE));
        Instant endedAt = Instant.now();
        return new TaskExecutionResult(
                TaskExecutionResult.CURRENT_SCHEMA_VERSION, executionId, runId, attempt,
                failureState(error), startedAt, endedAt, Duration.between(startedAt, endedAt).toMillis(),
                null, List.of(), ExecutionTaskType.SPARK_MODEL_QUALITY,
                ModelQualityExecutionResult.technicalFailure(null, List.of(), List.of(), null), error);
    }

    private TaskExecutionResult executeWithSpark(
            TaskExecutionManifest manifest,
            SparkSession spark,
            Instant startedAt,
            QualitySampleArtifactWriter sampleWriter
    ) {
        ModelQualityExecutionPayload payload = manifest.modelQuality();
        Map<UUID, RuntimeDataSource> runtimeSources = CanvasTaskExecutor.runtimeSources(manifest.runtimeDataSources());
        Map<UUID, ModelQualityExecutionPayload.QualityModelSnapshot> models = new LinkedHashMap<>();
        models.put(payload.targetModel().id(), payload.targetModel());
        payload.referenceModels().forEach(model -> models.put(model.id(), model));
        Map<UUID, ModelQualityExecutionPayload.QualityDictionarySnapshot> dictionaries = new LinkedHashMap<>();
        payload.dictionaries().forEach(dictionary -> dictionaries.put(dictionary.id(), dictionary));

        Dataset<Row> target = readModel(spark, runtimeSources, payload.targetModel())
                .persist(StorageLevel.MEMORY_AND_DISK());
        Map<UUID, Dataset<Row>> referenceCache = new LinkedHashMap<>();
        try {
            long checkedRows = target.count();
            List<QualityRuleExecutionResult> results = new ArrayList<>();
            List<ModelQualityExecutionPayload.QualityRuleSnapshot> ordered = payload.rules().stream()
                    .sorted(Comparator.comparing(ModelQualityExecutionPayload.QualityRuleSnapshot::severity)
                            .thenComparing(ModelQualityExecutionPayload.QualityRuleSnapshot::name))
                    .toList();
            for (ModelQualityExecutionPayload.QualityRuleSnapshot rule : ordered) {
                Instant ruleStartedAt = Instant.now();
                LOGGER.info("event=QUALITY_RULE_START executionId={} ruleId={} ruleName={} ruleType={}",
                        manifest.execution().executionId(), rule.id(), safe(rule.name()), rule.type());
                try {
                    RuleEvaluation evaluation = executeRule(
                            rule, target, checkedRows, payload.targetModel(), models, runtimeSources,
                            referenceCache, dictionaries, spark, startedAt);
                    QualityRuleMetric metric = evaluation.metric();
                    boolean passed = passed(metric, checkedRows);
                    QualitySampleResult sample = sampleResult(
                            rule, passed, evaluation, payload, sampleWriter);
                    long duration = Duration.between(ruleStartedAt, Instant.now()).toMillis();
                    results.add(new QualityRuleExecutionResult(
                            rule.id(), rule.name(), rule.type(), rule.severity(),
                            passed ? QualityRuleState.PASSED : QualityRuleState.FAILED, duration, metric, sample));
                    LOGGER.info("event=QUALITY_RULE_SUCCESS executionId={} ruleId={} ruleName={} ruleType={} state={} durationMs={}",
                            manifest.execution().executionId(), rule.id(), safe(rule.name()), rule.type(),
                            passed ? "PASSED" : "FAILED", duration);
                } catch (Throwable throwable) {
                    TaskExecutionError classified = failureClassifier.classify(
                            throwable, RunnerFailureContext.task(ExecutionFailurePhase.PROCESS));
                    long duration = Duration.between(ruleStartedAt, Instant.now()).toMillis();
                    LOGGER.error("event=QUALITY_RULE_FAILED executionId={} ruleId={} ruleName={} ruleType={} code={} diagnosticId={} durationMs={}\n{}",
                            manifest.execution().executionId(), rule.id(), safe(rule.name()), rule.type(),
                            classified.code(), classified.diagnosticId(), duration,
                            RunnerLogSanitizer.stackTrace(throwable));
                    ModelQualityExecutionResult failedResult = ModelQualityExecutionResult.technicalFailure(
                            checkedRows, results, payload.skippedRules(), new QualityRuleTechnicalFailure(
                            rule.id(), rule.name(), rule.type(), rule.severity(), duration,
                            classified.diagnosticId()));
                    Instant endedAt = Instant.now();
                    return new TaskExecutionResult(
                            TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                            manifest.execution().executionId(), manifest.execution().runId(),
                            manifest.execution().attempt(), failureState(classified), startedAt, endedAt,
                            Duration.between(startedAt, endedAt).toMillis(), null, List.of(),
                            ExecutionTaskType.SPARK_MODEL_QUALITY, failedResult, classified);
                }
            }
            long passed = results.stream().filter(result -> result.state() == QualityRuleState.PASSED).count();
            long failed = results.size() - passed;
            long skipped = payload.skippedRules().size();
            QualityConclusion conclusion = failed == 0 ? QualityConclusion.PASSED : QualityConclusion.FAILED;
            ModelQualityExecutionResult qualityResult = new ModelQualityExecutionResult(
                    conclusion, results.size() + skipped, passed, failed, skipped, checkedRows,
                    results, payload.skippedRules());
            Instant endedAt = Instant.now();
            return new TaskExecutionResult(
                    TaskExecutionResult.CURRENT_SCHEMA_VERSION,
                    manifest.execution().executionId(), manifest.execution().runId(), manifest.execution().attempt(),
                    TaskExecutionState.SUCCESS, startedAt, endedAt,
                    Duration.between(startedAt, endedAt).toMillis(), null, List.of(),
                    ExecutionTaskType.SPARK_MODEL_QUALITY, qualityResult, null);
        } finally {
            referenceCache.values().forEach(Dataset::unpersist);
            target.unpersist();
        }
    }

    private RuleEvaluation executeRule(
            ModelQualityExecutionPayload.QualityRuleSnapshot rule,
            Dataset<Row> target,
            long totalRows,
            ModelQualityExecutionPayload.QualityModelSnapshot targetModel,
            Map<UUID, ModelQualityExecutionPayload.QualityModelSnapshot> models,
            Map<UUID, RuntimeDataSource> runtimeSources,
            Map<UUID, Dataset<Row>> referenceCache,
            Map<UUID, ModelQualityExecutionPayload.QualityDictionarySnapshot> dictionaries,
            SparkSession spark,
            Instant runnerStartedAt
    ) {
        Map<UUID, ModelQualityExecutionPayload.QualityFieldSnapshot> fields = fields(targetModel);
        ModelQualityRuleDefinition definition = rule.definition();
        if (definition instanceof ModelQualityRuleDefinition.RowCountDefinition rowCount) {
            return RuleEvaluation.metric(new QualityRuleMetric.RowCount(totalRows, rowCount.minimumRowCount()));
        }
        if (definition instanceof ModelQualityRuleDefinition.FreshnessDefinition freshness) {
            String code = field(fields, freshness.fieldId()).code();
            Row row = target.agg(max(col(code)).alias("maximumValue")).first();
            Instant maximum = row.isNullAt(0) ? null : instant(row.get(0));
            Long delay = maximum == null ? null : delayMinutes(maximum, runnerStartedAt);
            return RuleEvaluation.metric(new QualityRuleMetric.Freshness(maximum, delay, freshness.maximumDelayMinutes()));
        }
        if (definition instanceof ModelQualityRuleDefinition.ReferenceExistsDefinition reference) {
            ModelQualityExecutionPayload.QualityModelSnapshot referenceModel = models.get(reference.targetModelId());
            if (referenceModel == null) throw new RunnerExecutionException(
                    "REFERENCE_MODEL_MISSING", "引用目标模型快照不存在", null);
            Dataset<Row> referenced = referenceModel.id().equals(targetModel.id())
                    ? target
                    : referenceCache.computeIfAbsent(referenceModel.id(), ignored ->
                    readModel(spark, runtimeSources, referenceModel).persist(StorageLevel.MEMORY_AND_DISK()));
            List<String> sourceCodes = reference.mappings().stream()
                    .map(mapping -> field(fields, mapping.sourceFieldId()).code()).toList();
            Map<UUID, ModelQualityExecutionPayload.QualityFieldSnapshot> referenceFields = fields(referenceModel);
            List<String> targetCodes = reference.mappings().stream()
                    .map(mapping -> field(referenceFields, mapping.targetFieldId()).code()).toList();
            Dataset<Row> sourceRows = target.filter(allNotNull(sourceCodes));
            List<String> targetAliases = keyColumns(sourceCodes.size()).stream()
                    .map(code -> "__ds_reference_" + code).toList();
            Column[] targets = new Column[targetCodes.size()];
            for (int index = 0; index < targetCodes.size(); index++) {
                targets[index] = col(targetCodes.get(index)).alias(targetAliases.get(index));
            }
            Dataset<Row> targetKeys = referenced.select(targets).distinct();
            Column join = lit(true);
            for (int index = 0; index < sourceCodes.size(); index++) {
                join = join.and(sourceRows.col(sourceCodes.get(index)).equalTo(targetKeys.col(targetAliases.get(index))));
            }
            Dataset<Row> invalidRows = sourceRows.join(targetKeys, join, "left_anti");
            long violations = invalidRows.count();
            return RuleEvaluation.rows(
                    violationMetric(violations, totalRows, reference.tolerance()), invalidRows,
                    reference.mappings().stream().map(ModelQualityRuleDefinition.ReferenceFieldMapping::sourceFieldId).toList(),
                    List.of(reason("源字段组在引用目标模型中不存在")));
        }

        Column invalid;
        ViolationTolerance tolerance;
        if (definition instanceof ModelQualityRuleDefinition.NotNullDefinition item) {
            invalid = col(field(fields, item.fieldId()).code()).isNull();
            tolerance = item.tolerance();
        } else if (definition instanceof ModelQualityRuleDefinition.UniqueDefinition item) {
            List<String> codes = item.fieldIds().stream().map(id -> field(fields, id).code()).toList();
            Dataset<Row> nonNull = target.filter(allNotNull(codes));
            String countCode = internalCode(targetModel, "__ds_duplicate_count_internal");
            Dataset<Row> duplicateGroups = nonNull.groupBy(
                            codes.stream().map(org.apache.spark.sql.functions::col).toArray(Column[]::new))
                    .count().withColumnRenamed("count", countCode).filter(col(countCode).gt(1));
            Row duplicateCount = duplicateGroups.agg(sum(col(countCode))).first();
            long violations = duplicateCount.isNullAt(0) ? 0 : ((Number) duplicateCount.get(0)).longValue();
            Dataset<Row> invalidRows = nonNull.join(duplicateGroups, codes.toArray(String[]::new), "inner");
            return RuleEvaluation.rows(
                    violationMetric(violations, totalRows, item.tolerance()), invalidRows, item.fieldIds(),
                    List.of(
                            new QualitySampleArtifactWriter.DiagnosticColumn(
                                    "__ds_duplicate_count", "重复组记录数",
                                    PlatformTypeDefinition.of(PlatformDataType.LONG), col(countCode)),
                            reason("字段组合值重复")));
        } else if (definition instanceof ModelQualityRuleDefinition.ValueRangeDefinition item) {
            ModelQualityExecutionPayload.QualityFieldSnapshot rangeField = field(fields, item.fieldId());
            Column value = col(rangeField.code());
            invalid = value.isNotNull();
            Column out = lit(false);
            if (item.minimum() != null) {
                Object minimum = typedValue(item.minimum(), rangeField.type());
                out = out.or(item.minimumInclusive() ? value.lt(minimum) : value.leq(minimum));
            }
            if (item.maximum() != null) {
                Object maximum = typedValue(item.maximum(), rangeField.type());
                out = out.or(item.maximumInclusive() ? value.gt(maximum) : value.geq(maximum));
            }
            invalid = invalid.and(out);
            tolerance = item.tolerance();
        } else if (definition instanceof ModelQualityRuleDefinition.StringLengthDefinition item) {
            Column value = col(field(fields, item.fieldId()).code());
            Column out = lit(false);
            if (item.minimumLength() != null) out = out.or(length(value).lt(item.minimumLength()));
            if (item.maximumLength() != null) out = out.or(length(value).gt(item.maximumLength()));
            invalid = value.isNotNull().and(out);
            tolerance = item.tolerance();
        } else if (definition instanceof ModelQualityRuleDefinition.DictionaryMembershipDefinition item) {
            ModelQualityExecutionPayload.QualityFieldSnapshot field = field(fields, item.fieldId());
            ModelQualityExecutionPayload.QualityDictionarySnapshot dictionary = dictionaries.get(field.dictionaryId());
            if (dictionary == null || !dictionary.enabled()) throw new RunnerExecutionException(
                    "DICTIONARY_UNAVAILABLE", "码表执行快照不可用", null);
            Object[] values = dictionary.effectiveValues().stream()
                    .map(value -> typedValue(value, field.type()))
                    .toArray();
            invalid = col(field.code()).isNotNull().and(not(col(field.code()).isin(values)));
            tolerance = item.tolerance();
        } else if (definition instanceof ModelQualityRuleDefinition.GeometryValidDefinition item) {
            Column value = col(field(fields, item.fieldId()).code());
            invalid = value.isNotNull().and(not(call_function("ST_IsValid", value)));
            tolerance = item.tolerance();
        } else if (definition instanceof ModelQualityRuleDefinition.GeometryNonEmptyDefinition item) {
            Column value = col(field(fields, item.fieldId()).code());
            invalid = value.isNotNull().and(call_function("ST_IsEmpty", value));
            tolerance = item.tolerance();
        } else if (definition instanceof ModelQualityRuleDefinition.FormatPatternDefinition item) {
            String regex = item.patternKind() == FormatPatternKind.PRESET
                    ? preset(item.preset()) : "\\A(?:" + item.regex() + ")\\z";
            Pattern.compile(regex);
            Column value = col(field(fields, item.fieldId()).code());
            invalid = value.isNotNull().and(not(value.rlike(regex)));
            tolerance = item.tolerance();
        } else if (definition instanceof ModelQualityRuleDefinition.ConditionalNotNullDefinition item) {
            Column condition = condition(item.condition(), fields);
            invalid = condition.and(col(field(fields, item.targetFieldId()).code()).isNull());
            tolerance = item.tolerance();
        } else if (definition instanceof ModelQualityRuleDefinition.FieldComparisonDefinition item) {
            Column left = col(field(fields, item.leftFieldId()).code());
            Column right = col(field(fields, item.rightFieldId()).code());
            Column comparison = compare(left, item.operator(), right);
            invalid = left.isNotNull().and(right.isNotNull()).and(not(comparison));
            tolerance = item.tolerance();
        } else {
            throw new RunnerExecutionException("QUALITY_RULE_NOT_SUPPORTED", "质检规则类型不受支持", null);
        }
        Dataset<Row> invalidRows = target.filter(invalid);
        long violations = invalidRows.count();
        return RuleEvaluation.rows(
                violationMetric(violations, totalRows, tolerance), invalidRows,
                involvedFieldIds(definition), diagnostics(definition, fields));
    }

    private static Column condition(
            ModelQualityRuleDefinition.QualityCondition condition,
            Map<UUID, ModelQualityExecutionPayload.QualityFieldSnapshot> fields
    ) {
        Column value = col(field(fields, condition.fieldId()).code());
        PlatformTypeDefinition type = field(fields, condition.fieldId()).type();
        Object[] values = condition.values().stream().map(item -> typedValue(item, type)).toArray();
        return switch (condition.operator()) {
            case EQ -> value.isNotNull().and(value.equalTo(values[0]));
            case NE -> value.isNotNull().and(value.notEqual(values[0]));
            case IN -> value.isNotNull().and(value.isin(values));
            case NOT_IN -> value.isNotNull().and(not(value.isin(values)));
            case IS_NULL -> value.isNull();
            case IS_NOT_NULL -> value.isNotNull();
            case IS_EMPTY -> value.equalTo("");
            case IS_NOT_EMPTY -> value.isNotNull().and(value.notEqual(""));
        };
    }

    private static Column compare(Column left, QualityFieldComparisonOperator operator, Column right) {
        return switch (operator) {
            case EQ -> left.equalTo(right);
            case NE -> left.notEqual(right);
            case LT -> left.lt(right);
            case LE -> left.leq(right);
            case GT -> left.gt(right);
            case GE -> left.geq(right);
        };
    }

    private static QualitySampleResult sampleResult(
            ModelQualityExecutionPayload.QualityRuleSnapshot rule,
            boolean passed,
            RuleEvaluation evaluation,
            ModelQualityExecutionPayload payload,
            QualitySampleArtifactWriter sampleWriter
    ) {
        if (passed) return QualitySampleResult.state(QualitySampleStatus.NOT_FAILED);
        if (rule.type() == cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType.ROW_COUNT
                || rule.type() == cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType.FRESHNESS) {
            return QualitySampleResult.state(QualitySampleStatus.NOT_APPLICABLE);
        }
        if (payload.failureSampleLimit() < 1) {
            return QualitySampleResult.state(QualitySampleStatus.DISABLED);
        }
        QualityRuleMetric.Violation metric = (QualityRuleMetric.Violation) evaluation.metric();
        return sampleWriter.write(
                rule.id(), evaluation.invalidRows(), payload.targetModel().fields(),
                evaluation.involvedFieldIds(), evaluation.diagnostics(), metric.violationCount());
    }

    private static List<UUID> involvedFieldIds(ModelQualityRuleDefinition definition) {
        if (definition instanceof ModelQualityRuleDefinition.NotNullDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.UniqueDefinition item) return item.fieldIds();
        if (definition instanceof ModelQualityRuleDefinition.ValueRangeDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.StringLengthDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.DictionaryMembershipDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.GeometryValidDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.GeometryNonEmptyDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.FormatPatternDefinition item) return List.of(item.fieldId());
        if (definition instanceof ModelQualityRuleDefinition.ConditionalNotNullDefinition item) {
            return List.of(item.targetFieldId(), item.condition().fieldId());
        }
        if (definition instanceof ModelQualityRuleDefinition.FieldComparisonDefinition item) {
            return List.of(item.leftFieldId(), item.rightFieldId());
        }
        return List.of();
    }

    private static List<QualitySampleArtifactWriter.DiagnosticColumn> diagnostics(
            ModelQualityRuleDefinition definition,
            Map<UUID, ModelQualityExecutionPayload.QualityFieldSnapshot> fields
    ) {
        List<QualitySampleArtifactWriter.DiagnosticColumn> result = new ArrayList<>();
        result.add(reason(reasonText(definition)));
        if (definition instanceof ModelQualityRuleDefinition.StringLengthDefinition item) {
            result.add(new QualitySampleArtifactWriter.DiagnosticColumn(
                    "__ds_actual_length", "实际字符串长度",
                    PlatformTypeDefinition.of(PlatformDataType.LONG),
                    length(col(field(fields, item.fieldId()).code())).cast("long")));
        }
        if (definition instanceof ModelQualityRuleDefinition.GeometryValidDefinition item) {
            result.add(geometryType(fields, item.fieldId()));
        }
        if (definition instanceof ModelQualityRuleDefinition.GeometryNonEmptyDefinition item) {
            result.add(geometryType(fields, item.fieldId()));
        }
        return List.copyOf(result);
    }

    private static QualitySampleArtifactWriter.DiagnosticColumn geometryType(
            Map<UUID, ModelQualityExecutionPayload.QualityFieldSnapshot> fields,
            UUID fieldId
    ) {
        return QualitySampleArtifactWriter.DiagnosticColumn.text(
                "__ds_geometry_type", "Geometry 类型",
                call_function("ST_GeometryType", col(field(fields, fieldId).code())));
    }

    private static QualitySampleArtifactWriter.DiagnosticColumn reason(String value) {
        return QualitySampleArtifactWriter.DiagnosticColumn.text(
                "__ds_reason", "异常原因", lit(value));
    }

    private static String reasonText(ModelQualityRuleDefinition definition) {
        if (definition instanceof ModelQualityRuleDefinition.NotNullDefinition) return "字段值为 NULL";
        if (definition instanceof ModelQualityRuleDefinition.ValueRangeDefinition) return "字段值超出允许范围";
        if (definition instanceof ModelQualityRuleDefinition.StringLengthDefinition) return "字符串长度超出允许范围";
        if (definition instanceof ModelQualityRuleDefinition.DictionaryMembershipDefinition) return "字段值不在有效码表成员中";
        if (definition instanceof ModelQualityRuleDefinition.GeometryValidDefinition) return "Geometry 无效";
        if (definition instanceof ModelQualityRuleDefinition.GeometryNonEmptyDefinition) return "Geometry 为空";
        if (definition instanceof ModelQualityRuleDefinition.FormatPatternDefinition) return "字段值格式不匹配";
        if (definition instanceof ModelQualityRuleDefinition.ConditionalNotNullDefinition) return "条件成立时目标字段为 NULL";
        if (definition instanceof ModelQualityRuleDefinition.FieldComparisonDefinition) return "字段比较不成立";
        return "质量规则未通过";
    }

    private static String internalCode(
            ModelQualityExecutionPayload.QualityModelSnapshot model,
            String preferred
    ) {
        Set<String> codes = model.fields().stream()
                .map(ModelQualityExecutionPayload.QualityFieldSnapshot::code).collect(java.util.stream.Collectors.toSet());
        String candidate = preferred;
        for (int suffix = 2; codes.contains(candidate); suffix++) candidate = preferred + "_" + suffix;
        return candidate;
    }

    private static QualityRuleMetric.Violation violationMetric(
            long violations,
            long totalRows,
            ViolationTolerance tolerance
    ) {
        BigDecimal percent = totalRows == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(violations).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalRows), 6, RoundingMode.HALF_UP).stripTrailingZeros();
        return new QualityRuleMetric.Violation(violations, percent, tolerance.metric(), tolerance.value());
    }

    private static boolean passed(QualityRuleMetric metric, long totalRows) {
        if (metric instanceof QualityRuleMetric.RowCount rowCount) return rowCount.actualRows() >= rowCount.minimumRows();
        if (metric instanceof QualityRuleMetric.Freshness freshness) {
            return freshness.maximumValue() != null && freshness.actualDelayMinutes() != null
                    && freshness.actualDelayMinutes() <= freshness.maximumDelayMinutes();
        }
        QualityRuleMetric.Violation violation = (QualityRuleMetric.Violation) metric;
        if (violation.toleranceMetric() == ViolationMetric.COUNT) {
            return BigDecimal.valueOf(violation.violationCount()).compareTo(violation.toleranceValue()) <= 0;
        }
        return BigDecimal.valueOf(violation.violationCount()).multiply(BigDecimal.valueOf(100))
                .compareTo(violation.toleranceValue().multiply(BigDecimal.valueOf(totalRows))) <= 0;
    }

    private static Dataset<Row> readModel(
            SparkSession spark,
            Map<UUID, RuntimeDataSource> runtimeSources,
            ModelQualityExecutionPayload.QualityModelSnapshot model
    ) {
        RuntimeDataSource source = CanvasTaskExecutor.requireRuntimeSourceForModelRead(
                runtimeSources, model.dataSourceId(), null);
        CanvasTableSchema schema = new CanvasTableSchema(
                model.code(),
                CanvasTableOrigin.model(model.id(), model.code(), model.schemaVersion()),
                model.fields().stream().map(field -> new CanvasColumnSchema(
                        field.code(), field.type().type(), field.type().length(), field.type().precision(),
                        field.type().scale(), field.nullable(), null, false, false, null,
                        field.type().geometry())).toList());
        return SpatialJdbcRuntimeSupport.readTable(
                spark,
                source,
                new TableIdentifier(model.catalogName(), model.schemaName(), model.physicalTableName()),
                schema,
                java.util.List.of(),
                null);
    }

    private static Object typedValue(String value, PlatformTypeDefinition type) {
        try {
            return switch (type.type()) {
                case STRING -> value;
                case BOOLEAN -> Boolean.valueOf(value);
                case BYTE -> Byte.valueOf(value);
                case SHORT -> Short.valueOf(value);
                case INTEGER -> Integer.valueOf(value);
                case LONG -> Long.valueOf(value);
                case FLOAT -> Float.valueOf(value);
                case DOUBLE -> Double.valueOf(value);
                case DECIMAL -> new BigDecimal(value);
                case DATE -> Date.valueOf(LocalDate.parse(value));
                case TIMESTAMP -> Timestamp.from(Instant.parse(value));
                case TIMESTAMP_NTZ -> LocalDateTime.parse(value);
                case BINARY, GEOMETRY -> throw new IllegalArgumentException("unsupported literal type");
            };
        } catch (RuntimeException exception) {
            throw new RunnerExecutionException(
                    "QUALITY_LITERAL_INVALID", "质检规则快照中的常量与字段类型不兼容", null, exception);
        }
    }

    private static Map<UUID, ModelQualityExecutionPayload.QualityFieldSnapshot> fields(
            ModelQualityExecutionPayload.QualityModelSnapshot model
    ) {
        Map<UUID, ModelQualityExecutionPayload.QualityFieldSnapshot> result = new LinkedHashMap<>();
        model.fields().forEach(field -> result.put(field.id(), field));
        return result;
    }

    private static ModelQualityExecutionPayload.QualityFieldSnapshot field(
            Map<UUID, ModelQualityExecutionPayload.QualityFieldSnapshot> fields,
            UUID fieldId
    ) {
        ModelQualityExecutionPayload.QualityFieldSnapshot field = fields.get(fieldId);
        if (field == null) throw new RunnerExecutionException("QUALITY_FIELD_MISSING", "质检字段快照不存在", null);
        return field;
    }

    private static Column allNotNull(List<String> columns) {
        Column condition = lit(true);
        for (String column : columns) condition = condition.and(col(column).isNotNull());
        return condition;
    }

    private static Column[] aliasColumns(List<String> columns, String prefix) {
        Column[] result = new Column[columns.size()];
        for (int index = 0; index < columns.size(); index++) result[index] = col(columns.get(index)).alias(prefix + index);
        return result;
    }

    private static List<String> keyColumns(int size) {
        List<String> result = new ArrayList<>();
        for (int index = 0; index < size; index++) result.add("key" + index);
        return result;
    }

    private static Instant instant(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Instant instant) return instant;
        if (value instanceof LocalDateTime dateTime) return dateTime.toInstant(ZoneOffset.UTC);
        if (value instanceof java.sql.Date date) return date.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        if (value instanceof LocalDate date) return date.atStartOfDay().toInstant(ZoneOffset.UTC);
        throw new RunnerExecutionException("FRESHNESS_VALUE_INVALID", "新鲜度字段值不能转换为 UTC 时间", null);
    }

    private static long delayMinutes(Instant maximum, Instant runnerStartedAt) {
        Duration delay = Duration.between(maximum, runnerStartedAt);
        if (delay.isNegative() || delay.isZero()) return 0;
        long seconds = delay.getSeconds();
        return Math.addExact(seconds, 59) / 60;
    }

    private static String preset(FormatPatternPreset preset) {
        if (preset == null) throw new RunnerExecutionException("FORMAT_PRESET_MISSING", "格式预置项不存在", null);
        return switch (preset) {
            case RESIDENT_ID_CARD -> "^(?:[1-9]\\d{5}(?:18|19|20)\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]|[1-9]\\d{5}\\d{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\\d|3[01])\\d{3})$";
            case UNIFIED_SOCIAL_CREDIT_CODE -> "^[0-9A-HJ-NPQRTUWXY]{18}$";
            case MAINLAND_MOBILE_PHONE -> "^1[3-9]\\d{9}$";
            case EMAIL -> "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$";
            case ADMINISTRATIVE_DIVISION_CODE -> "^[1-9]\\d{5}$";
        };
    }

    private static String safe(String value) {
        return value == null ? "" : value.replaceAll("[\\r\\n\\t]", " ");
    }

    private static TaskExecutionState failureState(TaskExecutionError error) {
        return switch (error.code()) {
            case "EXECUTION_DEADLINE_EXCEEDED" -> TaskExecutionState.TIMED_OUT;
            case "EXECUTION_CANCELLED" -> TaskExecutionState.CANCELLED;
            default -> TaskExecutionState.FAILED;
        };
    }

    private static void validateManifest(TaskExecutionManifest manifest) {
        ManifestVersionSupport.requireSupported(manifest);
        if (manifest.executionTaskType() != ExecutionTaskType.SPARK_MODEL_QUALITY
                || manifest.modelQuality() == null || manifest.task() != null
                || manifest.modelQuality().targetModel() == null || manifest.modelQuality().rules().isEmpty()
                || manifest.execution() == null || manifest.execution().deadlineAt() == null
                || manifest.runtimeDataSources() == null) {
            throw new RunnerExecutionException("INVALID_MANIFEST", "模型质检 manifest 不完整或载荷不互斥", null);
        }
    }

    private record RuleEvaluation(
            QualityRuleMetric metric,
            Dataset<Row> invalidRows,
            List<UUID> involvedFieldIds,
            List<QualitySampleArtifactWriter.DiagnosticColumn> diagnostics
    ) {
        RuleEvaluation {
            involvedFieldIds = involvedFieldIds == null ? List.of() : List.copyOf(involvedFieldIds);
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        static RuleEvaluation metric(QualityRuleMetric metric) {
            return new RuleEvaluation(metric, null, List.of(), List.of());
        }

        static RuleEvaluation rows(
                QualityRuleMetric metric,
                Dataset<Row> invalidRows,
                List<UUID> involvedFieldIds,
                List<QualitySampleArtifactWriter.DiagnosticColumn> diagnostics
        ) {
            return new RuleEvaluation(metric, invalidRows, involvedFieldIds, diagnostics);
        }
    }
}

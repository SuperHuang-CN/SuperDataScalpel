package cn.superhuang.data.scalpel.business.quality.service;

import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityOverviewResponse;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityOverviewResponse.EffectiveResult;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityOverviewResponse.Metric;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityOverviewResponse.RecentRun;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityOverviewResponse.ResultDetailStatus;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityOverviewResponse.RuleResult;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityOverviewResponse.RuleState;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityOverviewResponse.Sample;
import cn.superhuang.data.scalpel.business.quality.web.response.ModelQualityOverviewResponse.SampleStatus;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.task.service.TaskRunArtifactStorage;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRunExecutionErrorResponse;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleSeverity;
import cn.superhuang.data.scalpel.contract.quality.ModelQualityRuleType;
import cn.superhuang.data.scalpel.contract.quality.QualityConclusion;
import cn.superhuang.data.scalpel.contract.quality.ViolationMetric;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Model-facing projection over the latest immutable model-quality Result artifact. */
@Service
public class ModelQualityOverviewService {
    private static final int MAXIMUM_RESULT_BYTES = 5 * 1024 * 1024;

    private final DataModelRepository modelRepository;
    private final TaskRunRepository runRepository;
    private final DataTaskRepository taskRepository;
    private final ObjectProvider<TaskRunArtifactStorage> storageProvider;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate readTransaction;

    public ModelQualityOverviewService(
            DataModelRepository modelRepository,
            TaskRunRepository runRepository,
            DataTaskRepository taskRepository,
            ObjectProvider<TaskRunArtifactStorage> storageProvider,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.modelRepository = modelRepository;
        this.runRepository = runRepository;
        this.taskRepository = taskRepository;
        this.storageProvider = storageProvider;
        this.objectMapper = objectMapper;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    public ModelQualityOverviewResponse get(UUID modelId) {
        OverviewSnapshot snapshot = required(readTransaction.execute(status -> snapshot(modelId)));
        if (snapshot.effectiveRun() == null) {
            return response(snapshot, ResultDetailStatus.NOT_AVAILABLE, null, List.of());
        }
        TaskRunArtifactStorage storage = storageProvider.getIfAvailable();
        if (storage == null) {
            return response(snapshot, ResultDetailStatus.UNAVAILABLE, "任务制品存储尚未配置", List.of());
        }
        byte[] content;
        try {
            Optional<byte[]> stored = storage.readIfPresent(
                    snapshot.effectiveRun().resultObjectKey(), MAXIMUM_RESULT_BYTES);
            if (stored.isEmpty()) {
                return response(snapshot, ResultDetailStatus.UNAVAILABLE, "最近质检结果制品不存在", List.of());
            }
            content = stored.get();
        } catch (IllegalStateException exception) {
            if (exception.getMessage() != null && exception.getMessage().contains("超过允许大小")) {
                return response(snapshot, ResultDetailStatus.INVALID, "最近质检结果制品超过大小限制", List.of());
            }
            return response(snapshot, ResultDetailStatus.UNAVAILABLE, "任务制品存储当前不可用", List.of());
        } catch (RuntimeException exception) {
            return response(snapshot, ResultDetailStatus.UNAVAILABLE, "任务制品存储当前不可用", List.of());
        }
        try {
            List<RuleResult> rules = parseAndValidate(content, snapshot.effectiveRun());
            return response(snapshot, ResultDetailStatus.AVAILABLE, null, rules);
        } catch (RuntimeException exception) {
            return response(snapshot, ResultDetailStatus.INVALID, "最近质检结果制品无效", List.of());
        }
    }

    private OverviewSnapshot snapshot(UUID modelId) {
        if (!modelRepository.existsById(modelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在");
        }
        TaskRun latest = runRepository.findFirstByQualityTargetModelIdOrderByQueuedAtDesc(modelId).orElse(null);
        TaskRun effective = runRepository
                .findFirstByQualityTargetModelIdAndStatusAndQualityConclusionIsNotNullAndQualityRuleSnapshotAtIsNotNullOrderByEndedAtDesc(
                        modelId, TaskRunStatus.SUCCESS)
                .orElse(null);
        Set<UUID> taskIds = new HashSet<>();
        if (latest != null) taskIds.add(latest.getTaskId());
        if (effective != null) taskIds.add(effective.getTaskId());
        java.util.Map<UUID, String> taskNames = taskRepository.findAllById(taskIds).stream()
                .collect(java.util.stream.Collectors.toMap(DataTask::getId, DataTask::getName));
        return new OverviewSnapshot(
                modelId,
                latest == null ? null : runSnapshot(latest, taskNames.get(latest.getTaskId())),
                effective == null ? null : runSnapshot(effective, taskNames.get(effective.getTaskId())));
    }

    private static RunSnapshot runSnapshot(TaskRun run, String taskName) {
        return new RunSnapshot(
                run.getId(), run.getExecutionRunId(), run.getExternalExecutionId(), run.getTaskId(),
                taskName == null ? "任务已删除" : taskName, run.getTriggerType(), run.getStatus(),
                run.getAttempt(), run.getQueuedAt(), run.getStartedAt(), run.getEndedAt(), run.getMessage(),
                TaskRunExecutionErrorResponse.from(run), run.getResultObjectKey(), run.getQualityConclusion(),
                run.getQualityTotalRules(), run.getQualityPassedRules(), run.getQualityFailedRules(),
                run.getQualitySkippedRules(), run.getQualityCheckedRows(), run.getQualityRuleSnapshotAt());
    }

    private ModelQualityOverviewResponse response(
            OverviewSnapshot snapshot,
            ResultDetailStatus detailStatus,
            String detailMessage,
            List<RuleResult> ruleResults
    ) {
        RunSnapshot latest = snapshot.latestRun();
        RunSnapshot effective = snapshot.effectiveRun();
        return new ModelQualityOverviewResponse(
                snapshot.modelId(),
                latest == null ? null : new RecentRun(
                        latest.id(), latest.taskId(), latest.taskName(), latest.triggerType(), latest.status(),
                        latest.queuedAt(), latest.startedAt(), latest.endedAt(), latest.message(), latest.executionError()),
                effective == null ? null : new EffectiveResult(
                        effective.id(), effective.taskId(), effective.taskName(), effective.conclusion(),
                        effective.totalRules(), effective.passedRules(), effective.failedRules(),
                        effective.skippedRules(), effective.checkedRows(), effective.ruleSnapshotAt(),
                        effective.queuedAt(), effective.endedAt()),
                detailStatus, detailMessage, ruleResults);
    }

    private List<RuleResult> parseAndValidate(byte[] content, RunSnapshot run) {
        try {
            JsonNode root = objectMapper.readTree(content);
            JsonNode schemaNode = root.path("schemaVersion");
            JsonNode attemptNode = root.path("attempt");
            int schemaVersion = schemaNode.asInt(-1);
            if (run.attempt() == null || !schemaNode.isIntegralNumber()
                    || !attemptNode.isIntegralNumber() || (schemaVersion != 4 && schemaVersion != 5)
                    || !"SPARK_MODEL_QUALITY".equals(text(root, "taskType"))
                    || !uuid(root, "executionId").equals(run.executionId())
                    || !uuid(root, "runId").equals(run.executionRunId())
                    || root.path("attempt").asInt(-1) != run.attempt()
                    || !"SUCCESS".equals(text(root, "state"))
                    || !root.path("error").isNull()
                    || !root.path("affectedRows").isNull()
                    || !root.path("nodeResults").isArray() || !root.path("nodeResults").isEmpty()) {
                throw new IllegalArgumentException("质检结果身份无效");
            }
            JsonNode quality = root.path("qualityResult");
            if (!quality.isObject() || !quality.path("technicalFailure").isNull()
                    || run.conclusion() == null || run.totalRules() == null
                    || run.passedRules() == null || run.failedRules() == null
                    || run.skippedRules() == null || run.checkedRows() == null
                    || run.ruleSnapshotAt() == null || run.endedAt() == null) {
                throw new IllegalArgumentException("质检运行汇总无效");
            }
            QualityConclusion conclusion = enumValue(QualityConclusion.class, quality, "conclusion");
            long total = nonNegativeLong(quality, "totalRules");
            long passed = nonNegativeLong(quality, "passedRules");
            long failed = nonNegativeLong(quality, "failedRules");
            long skipped = nonNegativeLong(quality, "skippedRules");
            long checkedRows = nonNegativeLong(quality, "checkedRows");
            if (conclusion != run.conclusion() || total != run.totalRules() || passed != run.passedRules()
                    || failed != run.failedRules() || skipped != run.skippedRules()
                    || checkedRows != run.checkedRows() || total != passed + failed + skipped
                    || conclusion == QualityConclusion.PASSED && failed != 0
                    || conclusion == QualityConclusion.FAILED && failed == 0) {
                throw new IllegalArgumentException("质检结果汇总不一致");
            }
            List<RuleResult> results = new ArrayList<>();
            Set<UUID> ruleIds = new HashSet<>();
            JsonNode executed = quality.path("ruleResults");
            JsonNode skippedRules = quality.path("skippedRuleResults");
            if (!executed.isArray() || !skippedRules.isArray()
                    || executed.isEmpty() || executed.size() != passed + failed
                    || skippedRules.size() != skipped) {
                throw new IllegalArgumentException("质检规则数量不一致");
            }
            long parsedPassed = 0;
            long parsedFailed = 0;
            for (JsonNode rule : executed) {
                UUID ruleId = uuid(rule, "ruleId");
                if (!ruleIds.add(ruleId)) throw new IllegalArgumentException("质检规则重复");
                ModelQualityRuleType type = enumValue(ModelQualityRuleType.class, rule, "ruleType");
                RuleState state = enumValue(RuleState.class, rule, "state");
                if (state == RuleState.SKIPPED) throw new IllegalArgumentException("执行规则状态无效");
                if (state == RuleState.PASSED) parsedPassed++;
                else parsedFailed++;
                long durationMs = nonNegativeLong(rule, "durationMs");
                Metric metric = metric(rule.path("metric"), type, checkedRows, state);
                Sample sample = sample(rule.path("sample"), schemaVersion, type, state, metric);
                results.add(new RuleResult(
                        ruleId, requiredText(rule, "ruleName", 100), type,
                        enumValue(ModelQualityRuleSeverity.class, rule, "severity"), state,
                        durationMs, metric, null, null, sample));
            }
            if (parsedPassed != passed || parsedFailed != failed) {
                throw new IllegalArgumentException("质检规则状态与汇总不一致");
            }
            for (JsonNode rule : skippedRules) {
                UUID ruleId = uuid(rule, "id");
                if (!ruleIds.add(ruleId)) throw new IllegalArgumentException("质检规则重复");
                results.add(new RuleResult(
                        ruleId, requiredText(rule, "name", 100),
                        enumValue(ModelQualityRuleType.class, rule, "type"), null,
                        RuleState.SKIPPED, null, null,
                        requiredCode(rule, "reasonCode"), requiredText(rule, "reason", 1000), null));
            }
            return List.copyOf(results);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("质检结果无法解析", exception);
        }
    }

    private static Metric metric(JsonNode metric, ModelQualityRuleType type, long checkedRows, RuleState state) {
        String kind = text(metric, "kind");
        boolean passed;
        Metric result;
        if ("VIOLATION".equals(kind)) {
            if (type == ModelQualityRuleType.ROW_COUNT || type == ModelQualityRuleType.FRESHNESS) {
                throw new IllegalArgumentException("规则指标类型无效");
            }
            long count = nonNegativeLong(metric, "violationCount");
            BigDecimal percent = decimal(metric, "violationPercent");
            ViolationMetric toleranceMetric = enumValue(ViolationMetric.class, metric, "toleranceMetric");
            BigDecimal toleranceValue = decimal(metric, "toleranceValue");
            if (count > checkedRows || percent.signum() < 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0
                    || toleranceValue.signum() < 0 || toleranceMetric == ViolationMetric.PERCENT
                    && toleranceValue.compareTo(BigDecimal.valueOf(100)) > 0
                    || toleranceMetric == ViolationMetric.COUNT
                    && toleranceValue.stripTrailingZeros().scale() > 0) {
                throw new IllegalArgumentException("异常指标无效");
            }
            BigDecimal expectedPercent = checkedRows == 0 ? BigDecimal.ZERO
                    : BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(checkedRows), 6, RoundingMode.HALF_UP).stripTrailingZeros();
            if (percent.compareTo(expectedPercent) != 0) {
                throw new IllegalArgumentException("异常比例与检查行数不一致");
            }
            passed = toleranceMetric == ViolationMetric.COUNT
                    ? BigDecimal.valueOf(count).compareTo(toleranceValue) <= 0
                    : BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                    .compareTo(toleranceValue.multiply(BigDecimal.valueOf(checkedRows))) <= 0;
            result = Metric.violation(count, percent, toleranceMetric, toleranceValue);
        } else if ("ROW_COUNT".equals(kind)) {
            long actual = nonNegativeLong(metric, "actualRows");
            long minimum = positiveLong(metric, "minimumRows");
            if (type != ModelQualityRuleType.ROW_COUNT || actual != checkedRows) {
                throw new IllegalArgumentException("行数指标无效");
            }
            passed = actual >= minimum;
            result = Metric.rowCount(actual, minimum);
        } else if ("FRESHNESS".equals(kind)) {
            long maximumDelay = positiveLong(metric, "maximumDelayMinutes");
            JsonNode maximumNode = metric.path("maximumValue");
            JsonNode actualNode = metric.path("actualDelayMinutes");
            Instant maximumValue;
            if (maximumNode.isNull()) {
                maximumValue = null;
            } else if (maximumNode.isTextual()) {
                maximumValue = Instant.parse(maximumNode.asText());
            } else {
                throw new IllegalArgumentException("最大时间指标无效");
            }
            Long actualDelay = actualNode.isNull() ? null : nonNegativeLong(metric, "actualDelayMinutes");
            if (type != ModelQualityRuleType.FRESHNESS || (maximumValue == null) != (actualDelay == null)) {
                throw new IllegalArgumentException("新鲜度指标无效");
            }
            passed = actualDelay != null && actualDelay <= maximumDelay;
            result = Metric.freshness(maximumValue, actualDelay, maximumDelay);
        } else {
            throw new IllegalArgumentException("规则指标类型未知");
        }
        if (passed != (state == RuleState.PASSED)) throw new IllegalArgumentException("规则结论与指标不一致");
        return result;
    }

    private static Sample sample(
            JsonNode sample,
            int schemaVersion,
            ModelQualityRuleType type,
            RuleState state,
            Metric metric
    ) {
        if (schemaVersion == 4) {
            if (!sample.isMissingNode() && !sample.isNull()) throw new IllegalArgumentException("v4 样本描述无效");
            return null;
        }
        SampleStatus status = enumValue(SampleStatus.class, sample, "status");
        if (state == RuleState.PASSED && status != SampleStatus.NOT_FAILED) {
            throw new IllegalArgumentException("通过规则样本状态无效");
        }
        boolean nonRowLevel = type == ModelQualityRuleType.ROW_COUNT || type == ModelQualityRuleType.FRESHNESS;
        if (state == RuleState.FAILED && nonRowLevel && status != SampleStatus.NOT_APPLICABLE
                || state == RuleState.FAILED && !nonRowLevel
                && status != SampleStatus.DISABLED && status != SampleStatus.AVAILABLE) {
            throw new IllegalArgumentException("失败规则样本状态无效");
        }
        if (status != SampleStatus.AVAILABLE) {
            requireEmptySample(sample);
            return new Sample(status, null, null, null, null);
        }
        long sampled = positiveLong(sample, "sampledRows");
        long violations = positiveLong(sample, "violationRows");
        if (sampled > 1000 || sampled > violations || metric.violationCount() == null
                || metric.violationCount() != violations
                || !sample.path("truncated").isBoolean() || !sample.path("rowLocatable").isBoolean()
                || sample.path("truncated").asBoolean() != (sampled < violations)
                || nonNegativeLong(sample, "sizeBytes") < 8
                || nonNegativeLong(sample, "sizeBytes") > 20L * 1024 * 1024
                || !requiredText(sample, "sha256", 64).matches("[0-9a-f]{64}")
                || !validSampleColumns(sample.path("columns"))) {
            throw new IllegalArgumentException("可用样本描述无效");
        }
        return new Sample(status, sampled, violations,
                sample.path("truncated").asBoolean(), sample.path("rowLocatable").asBoolean());
    }

    private static void requireEmptySample(JsonNode sample) {
        if (!sample.path("sampledRows").isNull() || !sample.path("violationRows").isNull()
                || !sample.path("truncated").isNull() || !sample.path("sizeBytes").isNull()
                || !sample.path("sha256").isNull() || !sample.path("rowLocatable").isNull()
                || !sample.path("columns").isArray() || !sample.path("columns").isEmpty()) {
            throw new IllegalArgumentException("样本状态描述无效");
        }
    }

    private static boolean validSampleColumns(JsonNode columns) {
        if (!columns.isArray() || columns.isEmpty()) return false;
        Set<String> codes = new HashSet<>();
        for (JsonNode column : columns) {
            String code = text(column, "code");
            String name = text(column, "name");
            JsonNode fieldId = column.path("fieldId");
            if (code == null || code.isBlank() || code.length() > 200 || !codes.add(code)
                    || name == null || name.isBlank() || name.length() > 200
                    || !column.path("type").isObject()
                    || !column.path("primaryKey").isBoolean() || !column.path("diagnostic").isBoolean()
                    || fieldId.isMissingNode()
                    || fieldId.isNull() != column.path("diagnostic").asBoolean()) {
                return false;
            }
            if (!fieldId.isNull()) {
                try {
                    UUID.fromString(fieldId.asText());
                } catch (RuntimeException exception) {
                    return false;
                }
            }
            if (column.path("diagnostic").asBoolean() && column.path("primaryKey").asBoolean()) return false;
        }
        return true;
    }

    private static UUID uuid(JsonNode node, String field) {
        return UUID.fromString(requiredText(node, field, 36));
    }

    private static String requiredCode(JsonNode node, String field) {
        String value = requiredText(node, field, 100);
        if (!value.matches("[A-Z][A-Z0-9_]{0,99}")) throw new IllegalArgumentException("错误码无效");
        return value;
    }

    private static String requiredText(JsonNode node, String field, int maximumLength) {
        String value = text(node, field);
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException("字段无效：" + field);
        }
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static long nonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || value.asLong(-1) < 0) throw new IllegalArgumentException("数值无效：" + field);
        return value.asLong();
    }

    private static long positiveLong(JsonNode node, String field) {
        long value = nonNegativeLong(node, field);
        if (value < 1) throw new IllegalArgumentException("数值无效：" + field);
        return value;
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isNumber()) throw new IllegalArgumentException("数值无效：" + field);
        return value.decimalValue();
    }

    private static <T extends Enum<T>> T enumValue(Class<T> type, JsonNode node, String field) {
        try {
            return Enum.valueOf(type, requiredText(node, field, 64));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("枚举无效：" + field, exception);
        }
    }

    private static <T> T required(T value) {
        return Objects.requireNonNull(value, "事务未返回质检概览快照");
    }

    private record OverviewSnapshot(UUID modelId, RunSnapshot latestRun, RunSnapshot effectiveRun) {
    }

    private record RunSnapshot(
            UUID id,
            UUID executionRunId,
            UUID executionId,
            UUID taskId,
            String taskName,
            cn.superhuang.data.scalpel.business.task.domain.TaskRunTriggerType triggerType,
            TaskRunStatus status,
            Integer attempt,
            Instant queuedAt,
            Instant startedAt,
            Instant endedAt,
            String message,
            TaskRunExecutionErrorResponse executionError,
            String resultObjectKey,
            QualityConclusion conclusion,
            Long totalRules,
            Long passedRules,
            Long failedRules,
            Long skippedRules,
            Long checkedRows,
            Instant ruleSnapshotAt
    ) {
    }
}

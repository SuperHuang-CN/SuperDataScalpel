package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.MetadataModel;
import cn.superhuang.data.scalpel.contract.task.MetadataModelField;
import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;
import cn.superhuang.datascalpel.sdk.JdbcTableIdentifier;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageAnalyzer;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageAnalysisLimits;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageMetadata;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageOutputCandidate;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.LineageHash;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Best-effort runtime lineage collector. It only inspects analyzed plans and never starts an action. */
final class SparkJarLineageRuntime {
    private static final int MAX_ASSETS_PER_FLOW = 200;
    private static final int MAX_FIELDS_PER_FLOW = 4_000;
    private static final int MAX_RELATIONS_PER_FLOW = 10_000;
    private static final int MAX_FLOWS = 100;
    private static final int MAX_TOTAL_ASSETS = 300;
    private static final int MAX_TOTAL_FIELDS = 2_000;
    private static final int MAX_TOTAL_EDGES = 3_000;
    private static final int MAX_TOTAL_USAGES = 2_000;
    private static final int MAX_TOTAL_WARNINGS = 100;

    private final boolean enabled;
    private final CatalystLineageAnalyzer analyzer = new CatalystLineageAnalyzer(
            CatalystLineageAnalysisLimits.jarRuntimeDefaults());
    private final Map<String, TaskLineageEvidence.Flow> attempted = new LinkedHashMap<>();
    private final Map<String, TaskLineageEvidence.Flow> confirmed = new LinkedHashMap<>();

    SparkJarLineageRuntime(boolean enabled) {
        this.enabled = enabled;
    }

    Dataset<Row> modelInput(String bindingName, MetadataModel model, Dataset<Row> source) {
        if (!enabled) return source;
        try {
            TaskLineageEvidence.Asset asset = new TaskLineageEvidence.Asset(
                    "input:model:" + model.id(), TaskLineageEvidence.AssetRole.INPUT,
                    TaskLineageEvidence.AssetKind.MODEL, null, null,
                    model.id(), model.schemaVersion(), null, null, null, null,
                    null, null, model.name()
            );
            Map<String, CatalystLineageMetadata.InputField> fields = model.fields().stream()
                    .collect(Collectors.toMap(
                            MetadataModelField::code,
                            field -> new CatalystLineageMetadata.InputField(
                                    "model-field:" + field.id(), field.id()),
                            (left, right) -> left,
                            LinkedHashMap::new
                    ));
            return CatalystLineageMetadata.markInput(
                    source, "jar:model-read:" + safeHash(bindingName), asset, fields);
        } catch (RuntimeException ignored) {
            return source;
        }
    }

    Dataset<Row> jdbcTableInput(
            String bindingName,
            UUID dataSourceId,
            JdbcTableIdentifier table,
            Dataset<Row> source
    ) {
        if (!enabled) return source;
        try {
            String identity = jdbcIdentity(dataSourceId, table.catalog(), table.schema(), table.table());
            TaskLineageEvidence.Asset asset = new TaskLineageEvidence.Asset(
                    "input:jdbc:" + LineageHash.sha256(identity), TaskLineageEvidence.AssetRole.INPUT,
                    TaskLineageEvidence.AssetKind.JDBC_TABLE, null, null,
                    null, null, dataSourceId, table.catalog(), table.schema(), table.table(),
                    null, null, table.table()
            );
            return CatalystLineageMetadata.markInput(
                    source, "jar:jdbc-table-read:" + safeHash(bindingName + "\0" + identity), asset, Map.of());
        } catch (RuntimeException ignored) {
            return source;
        }
    }

    Dataset<Row> jdbcQueryInput(
            String bindingName,
            UUID dataSourceId,
            String normalizedSql,
            Dataset<Row> source
    ) {
        if (!enabled) return source;
        try {
            String queryHash = LineageHash.sha256(normalizedSql);
            TaskLineageEvidence.Asset asset = new TaskLineageEvidence.Asset(
                    "input:jdbc-query:" + LineageHash.sha256(dataSourceId + "\0" + queryHash),
                    TaskLineageEvidence.AssetRole.INPUT,
                    TaskLineageEvidence.AssetKind.EXTERNAL_RESOURCE,
                    TaskLineageEvidence.ExternalResourceType.JDBC_QUERY_RESULT, null,
                    null, null, dataSourceId, null, null, null,
                    null, queryHash, bindingName + " 查询结果"
            );
            return CatalystLineageMetadata.markInput(
                    source, "jar:jdbc-query-read:" + safeHash(bindingName + "\0" + queryHash), asset, Map.of());
        } catch (RuntimeException ignored) {
            return source;
        }
    }

    PreparedFlow analyzeModelWrite(
            String bindingName,
            MetadataModel model,
            String writeMode,
            Dataset<Row> projected
    ) {
        if (!enabled) return PreparedFlow.disabled();
        Map<String, MetadataModelField> fields = model.fields().stream()
                .collect(Collectors.toMap(MetadataModelField::code, Function.identity()));
        List<CatalystLineageOutputCandidate.TargetField> targets = model.columns().stream()
                .map(column -> modelTarget(column, fields.get(column.name())))
                .toList();
        TaskLineageEvidence.Asset output = new TaskLineageEvidence.Asset(
                "output", TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.MODEL,
                null, writeMode(writeMode), model.id(), model.schemaVersion(),
                null, null, null, null, null, null, model.name()
        );
        String flowKey = flowKey(bindingName, "model:" + model.id(), writeMode);
        return analyze(new CatalystLineageOutputCandidate(
                flowKey, "jar:write:" + flowKey.substring(4), "SDK_MODEL_WRITE",
                "jar:output:" + flowKey.substring(4), projected, output, targets));
    }

    PreparedFlow analyzeJdbcWrite(
            String bindingName,
            UUID dataSourceId,
            JdbcTableIdentifier table,
            String writeMode,
            Dataset<Row> projected
    ) {
        if (!enabled) return PreparedFlow.disabled();
        String identity = jdbcIdentity(dataSourceId, table.catalog(), table.schema(), table.table());
        TaskLineageEvidence.Asset output = new TaskLineageEvidence.Asset(
                "output", TaskLineageEvidence.AssetRole.OUTPUT, TaskLineageEvidence.AssetKind.JDBC_TABLE,
                null, writeMode(writeMode), null, null, dataSourceId,
                table.catalog(), table.schema(), table.table(), null, null, table.table()
        );
        List<CatalystLineageOutputCandidate.TargetField> targets = List.of(projected.schema().fields()).stream()
                .map(field -> new CatalystLineageOutputCandidate.TargetField(
                        "jdbc-column:" + LineageHash.sha256(field.name()), null,
                        field.name(), field.name(), TaskLineageEvidence.OutputEffect.NOT_WRITTEN))
                .toList();
        String flowKey = flowKey(bindingName, identity, writeMode);
        return analyze(new CatalystLineageOutputCandidate(
                flowKey, "jar:write:" + flowKey.substring(4), "SDK_JDBC_WRITE",
                "jar:output:" + flowKey.substring(4), projected, output, targets));
    }

    private PreparedFlow analyze(CatalystLineageOutputCandidate candidate) {
        TaskLineageEvidence.Flow flow;
        try {
            TaskLineageEvidence evidence = analyzer.analyze(List.of(candidate));
            flow = evidence.flows().isEmpty()
                    ? unavailableFlow(candidate, "CATALYST_LINEAGE_UNAVAILABLE")
                    : enforceLimits(evidence.flows().getFirst());
        } catch (RuntimeException ignored) {
            flow = unavailableFlow(candidate, "CATALYST_LINEAGE_ANALYSIS_FAILED");
        }
        attempted.merge(flow.flowKey(), flow, SparkJarLineageRuntime::merge);
        return new PreparedFlow(flow);
    }

    void confirm(PreparedFlow prepared) {
        if (!enabled || prepared == null || prepared.flow() == null) return;
        try {
            confirmed.merge(prepared.flow().flowKey(), prepared.flow(), SparkJarLineageRuntime::merge);
        } catch (RuntimeException ignored) {
            // The business write has already succeeded. Lineage confirmation must remain best effort.
        }
    }

    TaskLineageEvidence evidence(boolean taskSucceeded) {
        if (!enabled) return null;
        try {
            return buildEvidence(taskSucceeded);
        } catch (RuntimeException ignored) {
            return TaskLineageEvidence.unavailable(
                    "LINEAGE_RESULT_UNAVAILABLE", "运行血缘结果生成失败，未影响用户作业");
        }
    }

    private TaskLineageEvidence buildEvidence(boolean taskSucceeded) {
        BoundedFlows bounded = boundForResult(
                (taskSucceeded ? confirmed : attempted).values());
        List<TaskLineageEvidence.Flow> flows = bounded.flows();
        if (flows.isEmpty()) {
            return TaskLineageEvidence.unavailable("NO_SDK_WRITES", "用户作业没有执行 SDK 写入");
        }
        TaskLineageEvidence.Coverage coverage = flows.stream().map(TaskLineageEvidence.Flow::coverage)
                .min(Comparator.comparingInt(SparkJarLineageRuntime::coverageRank))
                .orElse(TaskLineageEvidence.Coverage.MODEL_ONLY);
        TaskLineageEvidence.AnalysisStatus status = coverage == TaskLineageEvidence.Coverage.FIELD_COMPLETE
                ? TaskLineageEvidence.AnalysisStatus.COMPLETE
                : TaskLineageEvidence.AnalysisStatus.PARTIAL;
        LinkedHashSet<TaskLineageEvidence.Warning> warningSet = new LinkedHashSet<>();
        if (bounded.truncated()) {
            warningSet.add(new TaskLineageEvidence.Warning(
                    "LINEAGE_LIMIT_EXCEEDED", "运行血缘超过结果安全上限，已保留有限的资产级流向",
                    null, null, null));
        }
        flows.stream().flatMap(flow -> flow.warnings().stream())
                .limit(MAX_TOTAL_WARNINGS - warningSet.size()).forEach(warningSet::add);
        List<TaskLineageEvidence.Warning> warnings = List.copyOf(warningSet);
        return new TaskLineageEvidence(status, coverage, flows, warnings);
    }

    private static BoundedFlows boundForResult(java.util.Collection<TaskLineageEvidence.Flow> source) {
        List<TaskLineageEvidence.Flow> sorted = source.stream()
                .sorted(Comparator.comparing(TaskLineageEvidence.Flow::flowKey)).toList();
        boolean truncated = sorted.size() > MAX_FLOWS;
        List<TaskLineageEvidence.Flow> result = new ArrayList<>();
        int assets = 0;
        int fields = 0;
        int edges = 0;
        int usages = 0;
        for (TaskLineageEvidence.Flow original : sorted.stream().limit(MAX_FLOWS).toList()) {
            if (assets >= MAX_TOTAL_ASSETS) {
                truncated = true;
                break;
            }
            TaskLineageEvidence.Flow flow = enforceLimits(original);
            int remainingInputs = Math.max(0, MAX_TOTAL_ASSETS - assets - 1);
            boolean exceeds = flow.inputAssets().size() > remainingInputs
                    || fields + flow.fields().size() > MAX_TOTAL_FIELDS
                    || edges + flow.fieldEdges().size() > MAX_TOTAL_EDGES
                    || usages + flow.fieldUsages().size() > MAX_TOTAL_USAGES;
            if (exceeds) {
                flow = assetOnly(flow, remainingInputs,
                        "运行血缘超过结果安全上限，已保留资产级流向");
                truncated = true;
            } else if (flow.warnings().size() > 20) {
                List<TaskLineageEvidence.Warning> limited = new ArrayList<>(flow.warnings().subList(0, 19));
                limited.add(limitWarning(flow, "运行血缘警告超过结果安全上限，已截断"));
                flow = new TaskLineageEvidence.Flow(
                        flow.flowKey(), flow.producerKey(), flow.producerType(), flow.coverage(),
                        flow.outputAsset(), flow.inputAssets(), flow.fields(), flow.fieldEdges(),
                        flow.fieldUsages(), limited);
                truncated = true;
            }
            assets += 1 + flow.inputAssets().size();
            fields += flow.fields().size();
            edges += flow.fieldEdges().size();
            usages += flow.fieldUsages().size();
            result.add(flow);
        }
        return new BoundedFlows(List.copyOf(result), truncated);
    }

    private static CatalystLineageOutputCandidate.TargetField modelTarget(
            CanvasColumnSchema column,
            MetadataModelField field
    ) {
        TaskLineageEvidence.OutputEffect missing = column.autoIncrement() || column.generated()
                || column.defaultValue() != null
                ? TaskLineageEvidence.OutputEffect.DEFAULT_VALUE
                : TaskLineageEvidence.OutputEffect.NOT_WRITTEN;
        return new CatalystLineageOutputCandidate.TargetField(
                field == null ? "model-code:" + column.name() : "model-field:" + field.id(),
                field == null ? null : field.id(), column.name(),
                field == null ? column.name() : field.name(), missing);
    }

    private static TaskLineageEvidence.Flow enforceLimits(TaskLineageEvidence.Flow flow) {
        int relationCount = flow.fieldEdges().size() + flow.fieldUsages().size();
        if (flow.inputAssets().size() + 1 <= MAX_ASSETS_PER_FLOW
                && flow.fields().size() <= MAX_FIELDS_PER_FLOW
                && relationCount <= MAX_RELATIONS_PER_FLOW) return flow;
        return assetOnly(flow, MAX_ASSETS_PER_FLOW - 1,
                "运行血缘超过安全分析上限，已保留资产级流向");
    }

    private static TaskLineageEvidence.Flow assetOnly(
            TaskLineageEvidence.Flow flow,
            int maximumInputs,
            String message
    ) {
        List<TaskLineageEvidence.Asset> inputs = flow.inputAssets().stream()
                .sorted(Comparator.comparing(TaskLineageEvidence.Asset::localAssetKey))
                .limit(Math.max(0, maximumInputs)).toList();
        return new TaskLineageEvidence.Flow(
                flow.flowKey(), flow.producerKey(), flow.producerType(),
                TaskLineageEvidence.Coverage.MODEL_ONLY, flow.outputAsset(), inputs,
                List.of(), List.of(), List.of(), List.of(limitWarning(flow, message)));
    }

    private static TaskLineageEvidence.Warning limitWarning(
            TaskLineageEvidence.Flow flow,
            String message
    ) {
        return new TaskLineageEvidence.Warning(
                "LINEAGE_LIMIT_EXCEEDED", message, flow.producerKey(), flow.flowKey(), null);
    }

    private static TaskLineageEvidence.Flow unavailableFlow(
            CatalystLineageOutputCandidate candidate,
            String code
    ) {
        TaskLineageEvidence.Warning warning = new TaskLineageEvidence.Warning(
                code, "Catalyst 运行血缘暂时不可分析", candidate.producerKey(), candidate.flowKey(), null);
        return new TaskLineageEvidence.Flow(
                candidate.flowKey(), candidate.producerKey(), candidate.producerType(),
                TaskLineageEvidence.Coverage.MODEL_ONLY, candidate.asset(), List.of(),
                List.of(), List.of(), List.of(), List.of(warning));
    }

    private static TaskLineageEvidence.Flow merge(
            TaskLineageEvidence.Flow left,
            TaskLineageEvidence.Flow right
    ) {
        return new TaskLineageEvidence.Flow(
                left.flowKey(), left.producerKey(), left.producerType(),
                coverageRank(left.coverage()) <= coverageRank(right.coverage())
                        ? left.coverage() : right.coverage(),
                left.outputAsset(), union(left.inputAssets(), right.inputAssets()),
                union(left.fields(), right.fields()), union(left.fieldEdges(), right.fieldEdges()),
                union(left.fieldUsages(), right.fieldUsages()), union(left.warnings(), right.warnings())
        );
    }

    private static <T> List<T> union(List<T> left, List<T> right) {
        LinkedHashSet<T> values = new LinkedHashSet<>(left);
        values.addAll(right);
        return List.copyOf(values);
    }

    private static int coverageRank(TaskLineageEvidence.Coverage coverage) {
        return switch (coverage) {
            case MODEL_ONLY -> 0;
            case FIELD_PARTIAL -> 1;
            case FIELD_COMPLETE -> 2;
        };
    }

    private static TaskLineageEvidence.WriteMode writeMode(String mode) {
        return switch (mode) {
            case "APPEND" -> TaskLineageEvidence.WriteMode.APPEND;
            case "OVERWRITE" -> TaskLineageEvidence.WriteMode.FULL_OVERWRITE;
            case "UPSERT" -> TaskLineageEvidence.WriteMode.UPSERT;
            default -> throw new IllegalArgumentException("不支持的 SDK 写入模式");
        };
    }

    private static String jdbcIdentity(
            UUID dataSourceId,
            String catalog,
            String schema,
            String table
    ) {
        return String.join("\0", dataSourceId.toString(), Objects.toString(catalog, ""),
                Objects.toString(schema, ""), table);
    }

    private static String flowKey(String bindingName, String target, String mode) {
        return "jar:" + LineageHash.sha256(bindingName + "\0" + target + "\0" + mode);
    }

    private static String safeHash(String value) {
        return LineageHash.sha256(value);
    }

    record PreparedFlow(TaskLineageEvidence.Flow flow) {
        static PreparedFlow disabled() {
            return new PreparedFlow(null);
        }
    }

    private record BoundedFlows(List<TaskLineageEvidence.Flow> flows, boolean truncated) {
    }
}

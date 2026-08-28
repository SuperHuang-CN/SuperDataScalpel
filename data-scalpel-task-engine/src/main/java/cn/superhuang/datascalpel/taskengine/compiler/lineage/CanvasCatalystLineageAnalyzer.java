package cn.superhuang.datascalpel.taskengine.compiler.lineage;

import cn.superhuang.data.scalpel.contract.task.CanvasLineageCompilation;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;
import cn.superhuang.datascalpel.taskengine.canvas.CanvasLineageOutputCandidate;

import java.util.List;

/** Preserves the Canvas contract while delegating all Catalyst traversal to the shared core. */
public final class CanvasCatalystLineageAnalyzer {

    public CanvasLineageCompilation analyze(List<CanvasLineageOutputCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return CanvasLineageCompilation.unavailable(List.of(new CanvasLineageCompilation.Warning(
                    "NO_OUTPUT_FLOW", "Canvas 中没有可分析的输出节点", null, null, null)));
        }
        TaskLineageEvidence evidence = new CatalystLineageAnalyzer().analyze(
                candidates.stream().map(this::candidate).toList()
        );
        return new CanvasLineageCompilation(
                enumValue(evidence.analysisStatus(), CanvasLineageCompilation.AnalysisStatus.class),
                enumValue(evidence.coverage(), CanvasLineageCompilation.Coverage.class),
                evidence.flows().stream().map(this::flow).toList(),
                evidence.warnings().stream().map(this::warning).toList()
        );
    }

    private CatalystLineageOutputCandidate candidate(CanvasLineageOutputCandidate source) {
        String flowKey = source.outputWriteId() == null
                ? "canvas:" + source.node().id()
                : "canvas:" + source.node().id() + ":output-write:" + source.outputWriteId();
        return new CatalystLineageOutputCandidate(
                flowKey,
                source.node().id(),
                source.node().nodeType().name(),
                "canvas:output:" + source.node().id(),
                source.dataset(),
                asset(source.asset()),
                source.targetFields().stream().map(target -> new CatalystLineageOutputCandidate.TargetField(
                        target.localFieldKey(), target.modelFieldId(), target.columnCode(), target.columnName(),
                        enumValue(target.missingOutputEffect(), TaskLineageEvidence.OutputEffect.class)
                )).toList()
        );
    }

    private CanvasLineageCompilation.Flow flow(TaskLineageEvidence.Flow source) {
        return new CanvasLineageCompilation.Flow(
                source.flowKey(), source.producerKey(), CanvasNodeType.valueOf(source.producerType()),
                enumValue(source.coverage(), CanvasLineageCompilation.Coverage.class),
                asset(source.outputAsset()),
                source.inputAssets().stream().map(this::asset).toList(),
                source.fields().stream().map(this::field).toList(),
                source.fieldEdges().stream().map(this::edge).toList(),
                source.fieldUsages().stream().map(this::usage).toList(),
                source.warnings().stream().map(this::warning).toList()
        );
    }

    private TaskLineageEvidence.Asset asset(CanvasLineageCompilation.Asset source) {
        return new TaskLineageEvidence.Asset(
                source.localAssetKey(), enumValue(source.role(), TaskLineageEvidence.AssetRole.class),
                enumValue(source.kind(), TaskLineageEvidence.AssetKind.class),
                enumValue(source.externalResourceType(), TaskLineageEvidence.ExternalResourceType.class),
                enumValue(source.writeMode(), TaskLineageEvidence.WriteMode.class),
                source.modelId(), source.modelSchemaVersion(), source.dataSourceId(),
                source.catalogName(), source.schemaName(), source.physicalTableName(),
                source.resourceId(), source.resourceKeyHash(), source.safeDisplayName()
        );
    }

    private CanvasLineageCompilation.Asset asset(TaskLineageEvidence.Asset source) {
        return new CanvasLineageCompilation.Asset(
                source.localAssetKey(), enumValue(source.role(), CanvasLineageCompilation.AssetRole.class),
                enumValue(source.kind(), CanvasLineageCompilation.AssetKind.class),
                enumValue(source.externalResourceType(), CanvasLineageCompilation.ExternalResourceType.class),
                enumValue(source.writeMode(), CanvasLineageCompilation.WriteMode.class),
                source.modelId(), source.modelSchemaVersion(), source.dataSourceId(),
                source.catalogName(), source.schemaName(), source.physicalTableName(),
                source.resourceId(), source.resourceKeyHash(), source.safeDisplayName()
        );
    }

    private CanvasLineageCompilation.Field field(TaskLineageEvidence.Field source) {
        return new CanvasLineageCompilation.Field(
                source.localAssetKey(), source.localFieldKey(), source.modelFieldId(),
                source.columnCode(), source.columnName(), source.ordinal(),
                enumValue(source.outputEffect(), CanvasLineageCompilation.OutputEffect.class)
        );
    }

    private CanvasLineageCompilation.FieldEdge edge(TaskLineageEvidence.FieldEdge source) {
        return new CanvasLineageCompilation.FieldEdge(
                reference(source.source()), reference(source.target()), source.derivationKey(),
                enumValue(source.derivationType(), CanvasLineageCompilation.DerivationType.class),
                source.transformNodeKey()
        );
    }

    private CanvasLineageCompilation.FieldUsage usage(TaskLineageEvidence.FieldUsage source) {
        return new CanvasLineageCompilation.FieldUsage(
                reference(source.field()), source.nodeKey(),
                enumValue(source.usageType(), CanvasLineageCompilation.UsageType.class)
        );
    }

    private CanvasLineageCompilation.FieldReference reference(TaskLineageEvidence.FieldReference source) {
        return new CanvasLineageCompilation.FieldReference(source.localAssetKey(), source.localFieldKey());
    }

    private CanvasLineageCompilation.Warning warning(TaskLineageEvidence.Warning source) {
        return new CanvasLineageCompilation.Warning(
                source.code(), source.message(), source.producerKey(), source.flowKey(), source.outputOrdinal());
    }

    private static <S extends Enum<S>, T extends Enum<T>> T enumValue(S source, Class<T> target) {
        return source == null ? null : Enum.valueOf(target, source.name());
    }
}

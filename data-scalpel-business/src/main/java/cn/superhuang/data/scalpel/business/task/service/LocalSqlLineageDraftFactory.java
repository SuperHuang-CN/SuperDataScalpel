package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.lineage.domain.*;
import cn.superhuang.data.scalpel.business.lineage.service.TaskLineageSnapshotDraft;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.dialect.query.lineage.SqlLineageAnalysis;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Converts neutral SQL AST evidence plus JDBC output mapping into the lineage ingestion contract. */
@Component
public class LocalSqlLineageDraftFactory {

    public static final int GENERATOR_VERSION = 1;
    private static final String FLOW_KEY = "local-sql:output";

    public BuildResult create(
            UUID taskId,
            int definitionVersion,
            LocalSqlDefinitionInspectionRequest request,
            LocalSqlDefinitionInspection inspection
    ) {
        Assessment assessment = assess(inspection);
        SqlLineageAnalysis analysis = inspection.lineageEvidence().analysis();
        Map<UUID, LocalSqlDefinitionInspectionRequest.ModelWithFields> inputModels = request.inputs().stream()
                .collect(Collectors.toMap(input -> input.model().getId(), Function.identity()));
        var outputModel = request.output().model();

        Map<UUID, String> assetKeys = new LinkedHashMap<>();
        List<TaskLineageSnapshotDraft.AssetDraft> assets = new ArrayList<>();
        for (LocalSqlDefinitionInspectionRequest.ModelWithFields input : request.inputs()) {
            UUID modelId = input.model().getId();
            String originKey = originKey(modelId);
            String assetKey = assetKey(LineageAssetRole.INPUT, originKey);
            assetKeys.put(modelId, assetKey);
            assets.add(TaskLineageSnapshotDraft.AssetDraft.model(
                    assetKey, FLOW_KEY, originKey, LineageAssetRole.INPUT, null,
                    modelId, input.model().getSchemaVersion()
            ));
        }
        String outputOriginKey = originKey(outputModel.getId());
        String outputAssetKey = assetKey(LineageAssetRole.OUTPUT, outputOriginKey);
        assets.add(TaskLineageSnapshotDraft.AssetDraft.model(
                outputAssetKey, FLOW_KEY, outputOriginKey, LineageAssetRole.OUTPUT,
                request.writeMode() == cn.superhuang.data.scalpel.business.task.domain.LocalSqlWriteMode.APPEND
                        ? LineageWriteMode.APPEND : LineageWriteMode.FULL_OVERWRITE,
                outputModel.getId(), outputModel.getSchemaVersion()
        ));

        Map<String, DataModelField> outputByCode = request.output().fields().stream()
                .collect(Collectors.toMap(field -> normalize(field.getCode()), Function.identity()));
        Map<UUID, DataModelField> inputFields = request.inputs().stream().flatMap(input -> input.fields().stream())
                .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        Map<UUID, TaskLineageSnapshotDraft.FieldDraft> fields = new LinkedHashMap<>();
        List<TaskLineageSnapshotDraft.FieldEdgeDraft> edges = new ArrayList<>();
        List<TaskLineageSnapshotDraft.FieldUsageDraft> usages = new ArrayList<>();

        Map<Integer, DataModelField> writtenTargets = new LinkedHashMap<>();
        for (LocalSqlDefinitionInspectionColumn column : inspection.columns()) {
            if (column.matchedOutputFieldCode() != null) {
                DataModelField target = outputByCode.get(normalize(column.matchedOutputFieldCode()));
                if (target != null) writtenTargets.put(column.ordinal(), target);
            }
        }
        Map<Integer, SqlLineageAnalysis.Output> astOutputs = analysis.outputs().stream()
                .collect(Collectors.toMap(SqlLineageAnalysis.Output::ordinal, Function.identity(), (left, right) -> left));

        for (DataModelField target : request.output().fields()) {
            int outputOrdinal = writtenTargets.entrySet().stream()
                    .filter(entry -> entry.getValue().getId().equals(target.getId()))
                    .map(Map.Entry::getKey).findFirst().orElse(-1);
            SqlLineageAnalysis.Output evidence = astOutputs.get(outputOrdinal);
            LineageOutputFieldEffect effect = outputEffect(outputOrdinal, evidence, assessment.outputsAligned());
            fields.put(target.getId(), fieldDraft(outputAssetKey, target, effect));
            if (effect != LineageOutputFieldEffect.DERIVED) continue;

            String targetFieldKey = fieldKey(target.getId());
            String nodeKey = "local-sql:select:" + outputOrdinal;
            String derivationKey = "derive:" + sha256(String.join("\0",
                    FLOW_KEY, nodeKey, outputAssetKey, targetFieldKey, evidence.expressionFingerprint()));
            for (SqlLineageAnalysis.FieldReference source : evidence.sources()) {
                UUID sourceModelId = uuid(source.relationKey());
                UUID sourceFieldId = uuid(source.fieldKey());
                LocalSqlDefinitionInspectionRequest.ModelWithFields sourceModel = inputModels.get(sourceModelId);
                DataModelField sourceField = inputFields.get(sourceFieldId);
                String sourceAssetKey = assetKeys.get(sourceModelId);
                if (sourceModel == null || sourceField == null || sourceAssetKey == null) continue;
                fields.putIfAbsent(sourceFieldId, fieldDraft(sourceAssetKey, sourceField, null));
                edges.add(new TaskLineageSnapshotDraft.FieldEdgeDraft(
                        FLOW_KEY,
                        reference(sourceAssetKey, sourceFieldId),
                        reference(outputAssetKey, target.getId()),
                        derivationKey,
                        derivationType(evidence.kind()),
                        nodeKey
                ));
            }
        }

        for (SqlLineageAnalysis.FieldUsage usage : analysis.fieldUsages()) {
            UUID sourceModelId = uuid(usage.field().relationKey());
            UUID sourceFieldId = uuid(usage.field().fieldKey());
            DataModelField sourceField = inputFields.get(sourceFieldId);
            String sourceAssetKey = assetKeys.get(sourceModelId);
            if (sourceField == null || sourceAssetKey == null) continue;
            fields.putIfAbsent(sourceFieldId, fieldDraft(sourceAssetKey, sourceField, null));
            usages.add(new TaskLineageSnapshotDraft.FieldUsageDraft(
                    FLOW_KEY,
                    reference(sourceAssetKey, sourceFieldId),
                    usage.nodeKey(),
                    usageType(usage.kind())
            ));
        }

        TaskLineageSnapshotDraft draft = new TaskLineageSnapshotDraft(
                taskId, definitionVersion, assessment.coverage(), GENERATOR_VERSION,
                assets, List.copyOf(fields.values()), edges, usages
        );
        return new BuildResult(draft, assessment);
    }

    public Assessment assess(LocalSqlDefinitionInspection inspection) {
        SqlLineageAnalysis analysis = inspection.lineageEvidence().analysis();
        boolean outputsAligned = analysis.status() != SqlLineageAnalysis.AnalysisStatus.UNAVAILABLE
                && analysis.outputs().size() == inspection.columns().size();
        List<SqlLineageAnalysis.Warning> warnings = new ArrayList<>(analysis.warnings());
        if (analysis.status() != SqlLineageAnalysis.AnalysisStatus.UNAVAILABLE && !outputsAligned) {
            warnings.add(new SqlLineageAnalysis.Warning(
                    "AST_JDBC_OUTPUT_COUNT_MISMATCH",
                    "SQL AST 输出数量与 JDBC 实际结果列数量不一致，字段来源已降级为未知",
                    null
            ));
        }
        boolean writtenReliable = outputsAligned && analysis.outputs().stream().allMatch(SqlLineageAnalysis.Output::reliable);
        LineageCoverage coverage = analysis.status() == SqlLineageAnalysis.AnalysisStatus.COMPLETE && writtenReliable
                ? LineageCoverage.FIELD_COMPLETE : LineageCoverage.FIELD_PARTIAL;
        return new Assessment(coverage, analysis.status(), List.copyOf(warnings), outputsAligned);
    }

    private static LineageOutputFieldEffect outputEffect(
            int outputOrdinal,
            SqlLineageAnalysis.Output evidence,
            boolean outputsAligned
    ) {
        if (outputOrdinal < 1) return LineageOutputFieldEffect.NOT_WRITTEN;
        if (!outputsAligned || evidence == null || !evidence.reliable()) {
            return LineageOutputFieldEffect.WRITTEN_UNKNOWN_SOURCE;
        }
        return switch (evidence.kind()) {
            case DIRECT, CALCULATED, AGGREGATED -> evidence.sources().isEmpty()
                    ? LineageOutputFieldEffect.WRITTEN_UNKNOWN_SOURCE : LineageOutputFieldEffect.DERIVED;
            case CONSTANT -> LineageOutputFieldEffect.CONSTANT;
            case NULL_FILLED -> LineageOutputFieldEffect.NULL_FILLED;
            case UNKNOWN -> LineageOutputFieldEffect.WRITTEN_UNKNOWN_SOURCE;
        };
    }

    private static LineageFieldDerivationType derivationType(SqlLineageAnalysis.OutputKind kind) {
        return switch (kind) {
            case DIRECT -> LineageFieldDerivationType.DIRECT;
            case AGGREGATED -> LineageFieldDerivationType.AGGREGATED;
            default -> LineageFieldDerivationType.CALCULATED;
        };
    }

    private static LineageFieldUsageType usageType(SqlLineageAnalysis.FieldUsageKind kind) {
        return LineageFieldUsageType.valueOf(kind.name());
    }

    private static TaskLineageSnapshotDraft.FieldDraft fieldDraft(
            String assetKey,
            DataModelField field,
            LineageOutputFieldEffect outputEffect
    ) {
        return new TaskLineageSnapshotDraft.FieldDraft(
                assetKey, fieldKey(field.getId()), field.getId(), field.getCode(), field.getName(),
                field.getSortOrder(), outputEffect
        );
    }

    private static TaskLineageSnapshotDraft.FieldReference reference(String assetKey, UUID fieldId) {
        return new TaskLineageSnapshotDraft.FieldReference(assetKey, fieldKey(fieldId));
    }

    private static String originKey(UUID modelId) {
        return "model:" + modelId;
    }

    private static String assetKey(LineageAssetRole role, String originKey) {
        return "asset:" + sha256(String.join("\0", FLOW_KEY, role.name(), originKey));
    }

    private static String fieldKey(UUID fieldId) {
        return "model-field:" + fieldId;
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static UUID uuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public record Assessment(
            LineageCoverage coverage,
            SqlLineageAnalysis.AnalysisStatus analysisStatus,
            List<SqlLineageAnalysis.Warning> warnings,
            boolean outputsAligned
    ) {
    }

    public record BuildResult(TaskLineageSnapshotDraft draft, Assessment assessment) {
    }
}

package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageAssetKind;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageAssetRole;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageExternalResourceType;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageFieldDerivationType;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageFieldUsageType;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageOutputFieldEffect;
import cn.superhuang.data.scalpel.business.lineage.domain.LineageWriteMode;
import cn.superhuang.data.scalpel.business.lineage.service.TaskLineageSnapshotDraft;
import cn.superhuang.data.scalpel.contract.task.TaskLineageEvidence;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Converts Spark-free Catalyst evidence into the immutable lineage ingestion contract. */
@Component
public class SparkJarLineageDraftFactory {
    public static final int GENERATOR_VERSION = 3;

    public TaskLineageSnapshotDraft create(
            UUID taskId,
            int definitionVersion,
            TaskLineageEvidence compilation
    ) {
        Objects.requireNonNull(compilation, "compilation");
        if (compilation.flows().isEmpty()) {
            throw new IllegalArgumentException("JAR 运行结果没有可发布的输出血缘链路");
        }
        Map<String, TaskLineageSnapshotDraft.AssetDraft> assets = new LinkedHashMap<>();
        Map<String, TaskLineageSnapshotDraft.FieldDraft> fields = new LinkedHashMap<>();
        Map<String, TaskLineageSnapshotDraft.FieldEdgeDraft> edges = new LinkedHashMap<>();
        Map<String, TaskLineageSnapshotDraft.FieldUsageDraft> usages = new LinkedHashMap<>();

        for (TaskLineageEvidence.Flow flow : compilation.flows()) {
            LineageCoverage flowCoverage = coverage(flow.coverage());
            Map<String, String> assetKeys = new LinkedHashMap<>();
            for (TaskLineageEvidence.Asset input : flow.inputAssets()) {
                addAsset(flow.flowKey(), input, flowCoverage, assetKeys, assets);
            }
            addAsset(flow.flowKey(), flow.outputAsset(), flowCoverage, assetKeys, assets);
            if (flowCoverage == LineageCoverage.MODEL_ONLY) continue;

            for (TaskLineageEvidence.Field field : flow.fields()) {
                String assetKey = requireAssetKey(assetKeys, field.localAssetKey());
                TaskLineageSnapshotDraft.FieldDraft fieldDraft = new TaskLineageSnapshotDraft.FieldDraft(
                        assetKey,
                        fieldKey(field),
                        field.modelFieldId(),
                        field.columnCode(),
                        field.columnName(),
                        field.ordinal(),
                        field.outputEffect() == null ? null : LineageOutputFieldEffect.valueOf(
                                field.outputEffect().name())
                );
                fields.putIfAbsent(assetKey + '\0' + fieldDraft.fieldKey(), fieldDraft);
            }
            for (TaskLineageEvidence.FieldEdge edge : flow.fieldEdges()) {
                TaskLineageSnapshotDraft.FieldEdgeDraft edgeDraft = new TaskLineageSnapshotDraft.FieldEdgeDraft(
                        flow.flowKey(),
                        reference(assetKeys, edge.source()),
                        reference(assetKeys, edge.target()),
                        "derive:" + sha256(String.join("\0", flow.flowKey(), edge.derivationKey())),
                        LineageFieldDerivationType.valueOf(edge.derivationType().name()),
                        edge.transformNodeKey()
                );
                edges.putIfAbsent(String.join("\0", edgeDraft.flowKey(), edgeDraft.derivationKey(),
                        edgeDraft.source().assetKey(), edgeDraft.source().fieldKey(),
                        edgeDraft.target().assetKey(), edgeDraft.target().fieldKey()), edgeDraft);
            }
            for (TaskLineageEvidence.FieldUsage usage : flow.fieldUsages()) {
                TaskLineageSnapshotDraft.FieldUsageDraft usageDraft = new TaskLineageSnapshotDraft.FieldUsageDraft(
                        flow.flowKey(),
                        reference(assetKeys, usage.field()),
                        usage.nodeKey(),
                        LineageFieldUsageType.valueOf(usage.usageType().name())
                );
                usages.putIfAbsent(String.join("\0", usageDraft.flowKey(),
                        usageDraft.field().assetKey(), usageDraft.field().fieldKey(),
                        usageDraft.nodeKey(), usageDraft.usageType().name()), usageDraft);
            }
        }

        LineageCoverage snapshotCoverage = compilation.flows().stream()
                .map(TaskLineageEvidence.Flow::coverage)
                .map(SparkJarLineageDraftFactory::coverage)
                .min(Comparator.comparingInt(SparkJarLineageDraftFactory::coverageRank))
                .orElse(LineageCoverage.MODEL_ONLY);
        return new TaskLineageSnapshotDraft(
                taskId, definitionVersion, snapshotCoverage, GENERATOR_VERSION,
                List.copyOf(assets.values()), List.copyOf(fields.values()),
                List.copyOf(edges.values()), List.copyOf(usages.values())
        );
    }

    private static void addAsset(
            String flowKey,
            TaskLineageEvidence.Asset source,
            LineageCoverage flowCoverage,
            Map<String, String> assetKeys,
            Map<String, TaskLineageSnapshotDraft.AssetDraft> assets
    ) {
        String originKey = originKey(source);
        LineageAssetRole role = LineageAssetRole.valueOf(source.role().name());
        String assetKey = "asset:" + sha256(String.join("\0", flowKey, role.name(), originKey));
        if (assetKeys.putIfAbsent(source.localAssetKey(), assetKey) != null) {
            throw new IllegalArgumentException("JAR 运行血缘链路存在重复资产键：" + source.localAssetKey());
        }
        LineageWriteMode writeMode = source.writeMode() == null
                ? null : LineageWriteMode.valueOf(source.writeMode().name());
        LineageCoverage outputCoverage = role == LineageAssetRole.OUTPUT ? flowCoverage : null;
        TaskLineageSnapshotDraft.AssetDraft draft = new TaskLineageSnapshotDraft.AssetDraft(
                assetKey, flowKey, originKey, role,
                LineageAssetKind.valueOf(source.kind().name()), writeMode,
                source.modelId(), source.modelSchemaVersion(),
                source.kind() == TaskLineageEvidence.AssetKind.MODEL ? null : source.dataSourceId(),
                source.catalogName(), source.schemaName(), source.physicalTableName(),
                source.externalResourceType() == null ? null
                        : LineageExternalResourceType.valueOf(source.externalResourceType().name()),
                source.resourceId(), source.resourceKeyHash(), source.safeDisplayName(), outputCoverage
        );
        assets.putIfAbsent(assetKey, draft);
    }

    private static TaskLineageSnapshotDraft.FieldReference reference(
            Map<String, String> assetKeys,
            TaskLineageEvidence.FieldReference reference
    ) {
        return new TaskLineageSnapshotDraft.FieldReference(
                requireAssetKey(assetKeys, reference.localAssetKey()),
                fieldKey(reference.localFieldKey())
        );
    }

    private static String requireAssetKey(Map<String, String> assetKeys, String localAssetKey) {
        String key = assetKeys.get(localAssetKey);
        if (key == null) throw new IllegalArgumentException("JAR 运行血缘字段引用了未知资产：" + localAssetKey);
        return key;
    }

    private static String fieldKey(TaskLineageEvidence.Field field) {
        return field.modelFieldId() == null
                ? fieldKey(field.localFieldKey())
                : "model-field:" + field.modelFieldId();
    }

    private static String fieldKey(String localFieldKey) {
        return localFieldKey.startsWith("model-field:")
                ? localFieldKey
                : "field:" + sha256(localFieldKey);
    }

    private static String originKey(TaskLineageEvidence.Asset asset) {
        return switch (asset.kind()) {
            case MODEL -> "model:" + asset.modelId();
            case JDBC_TABLE -> "jdbc:" + sha256(String.join("\0",
                    text(asset.dataSourceId()), text(asset.catalogName()),
                    text(asset.schemaName()), text(asset.physicalTableName())));
            case EXTERNAL_RESOURCE -> "external:" + sha256(String.join("\0",
                    asset.externalResourceType().name(), text(asset.dataSourceId()),
                    text(asset.resourceId()), text(asset.resourceKeyHash())));
        };
    }

    private static LineageCoverage coverage(TaskLineageEvidence.Coverage value) {
        return value == null ? LineageCoverage.MODEL_ONLY : LineageCoverage.valueOf(value.name());
    }

    private static int coverageRank(LineageCoverage coverage) {
        return switch (coverage) {
            case MODEL_ONLY -> 0;
            case FIELD_PARTIAL -> 1;
            case FIELD_COMPLETE -> 2;
        };
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasLineageCompilation;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.FileOutputConflictPolicy;
import cn.superhuang.data.scalpel.contract.task.MetadataModel;
import cn.superhuang.data.scalpel.contract.task.MetadataModelField;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Compiler-only output identity paired with the exact Dataset presented to a writer. */
public record CanvasLineageOutputCandidate(
        CanvasNodeDefinition node,
        Dataset<Row> dataset,
        CanvasLineageCompilation.Asset asset,
        List<TargetField> targetFields
) {
    public CanvasLineageOutputCandidate {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(dataset, "dataset");
        Objects.requireNonNull(asset, "asset");
        targetFields = List.copyOf(targetFields);
    }

    public static CanvasLineageOutputCandidate model(
            CanvasNodeDefinition node,
            Dataset<Row> dataset,
            MetadataModel model,
            CanvasTableSchema targetSchema,
            CanvasLineageCompilation.WriteMode writeMode
    ) {
        Map<String, MetadataModelField> fields = model.fields().stream()
                .collect(Collectors.toMap(MetadataModelField::code, Function.identity()));
        List<TargetField> targetFields = targetSchema.columns().stream()
                .map(column -> {
                    MetadataModelField field = fields.get(column.name());
                    return new TargetField(
                            field == null ? "model-code:" + column.name() : "model-field:" + field.id(),
                            field == null ? null : field.id(), column.name(),
                            field == null ? column.name() : field.name(),
                            targetEffect(column)
                    );
                }).toList();
        return new CanvasLineageOutputCandidate(
                node, dataset,
                new CanvasLineageCompilation.Asset(
                        "output", CanvasLineageCompilation.AssetRole.OUTPUT,
                        CanvasLineageCompilation.AssetKind.MODEL, null, writeMode,
                        model.id(), model.schemaVersion(), model.dataSourceId(),
                        model.catalogName(), model.schemaName(), model.physicalTableName(),
                        null, null, model.name()
                ),
                targetFields
        );
    }

    public static CanvasLineageOutputCandidate jdbcTable(
            CanvasNodeDefinition node,
            Dataset<Row> dataset,
            UUID dataSourceId,
            MetadataTable table,
            CanvasLineageCompilation.WriteMode writeMode
    ) {
        return new CanvasLineageOutputCandidate(
                node, dataset,
                new CanvasLineageCompilation.Asset(
                        "output", CanvasLineageCompilation.AssetRole.OUTPUT,
                        CanvasLineageCompilation.AssetKind.JDBC_TABLE, null, writeMode,
                        null, null, dataSourceId, table.catalogName(), table.schemaName(),
                        table.physicalTableName(), null, null, table.tableName()
                ),
                table.columns().stream().map(column -> new TargetField(
                        "jdbc-column:" + sha256(column.name()), null,
                        column.name(), column.name(), targetEffect(column)
                )).toList()
        );
    }

    public static CanvasLineageOutputCandidate kafka(
            CanvasNodeDefinition node,
            Dataset<Row> dataset,
            UUID dataSourceId,
            String topic,
            CanvasTableSchema targetSchema
    ) {
        return external(
                node, dataset, CanvasLineageCompilation.ExternalResourceType.KAFKA_TOPIC,
                dataSourceId, null, sha256(topic), topic,
                CanvasLineageCompilation.WriteMode.APPEND, targetSchema
        );
    }

    public static CanvasLineageOutputCandidate file(
            CanvasNodeDefinition node,
            Dataset<Row> dataset,
            UUID dataSourceId,
            String targetPath,
            FileOutputConflictPolicy conflictPolicy,
            CanvasTableSchema targetSchema
    ) {
        return external(
                node, dataset, CanvasLineageCompilation.ExternalResourceType.OBJECT_STORAGE_PATH,
                dataSourceId, null, sha256(targetPath), node.name(),
                conflictPolicy == FileOutputConflictPolicy.OVERWRITE
                        ? CanvasLineageCompilation.WriteMode.FULL_OVERWRITE
                        : CanvasLineageCompilation.WriteMode.CREATE_NEW,
                targetSchema
        );
    }

    private static CanvasLineageOutputCandidate external(
            CanvasNodeDefinition node,
            Dataset<Row> dataset,
            CanvasLineageCompilation.ExternalResourceType resourceType,
            UUID dataSourceId,
            UUID resourceId,
            String resourceKeyHash,
            String displayName,
            CanvasLineageCompilation.WriteMode writeMode,
            CanvasTableSchema targetSchema
    ) {
        return new CanvasLineageOutputCandidate(
                node, dataset,
                new CanvasLineageCompilation.Asset(
                        "output", CanvasLineageCompilation.AssetRole.OUTPUT,
                        CanvasLineageCompilation.AssetKind.EXTERNAL_RESOURCE, resourceType, writeMode,
                        null, null, dataSourceId, null, null, null,
                        resourceId, resourceKeyHash, displayName
                ),
                targetSchema.columns().stream().map(column -> new TargetField(
                        "external-field:" + sha256(column.name()), null,
                        column.name(), column.name(), targetEffect(column)
                )).toList()
        );
    }

    private static CanvasLineageCompilation.OutputEffect targetEffect(
            cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema column
    ) {
        if (column.autoIncrement() || column.generated()) {
            return CanvasLineageCompilation.OutputEffect.DEFAULT_VALUE;
        }
        if (column.defaultValue() != null) {
            return CanvasLineageCompilation.OutputEffect.DEFAULT_VALUE;
        }
        return CanvasLineageCompilation.OutputEffect.NOT_WRITTEN;
    }

    public record TargetField(
            String localFieldKey,
            UUID modelFieldId,
            String columnCode,
            String columnName,
            CanvasLineageCompilation.OutputEffect missingOutputEffect
    ) {
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}

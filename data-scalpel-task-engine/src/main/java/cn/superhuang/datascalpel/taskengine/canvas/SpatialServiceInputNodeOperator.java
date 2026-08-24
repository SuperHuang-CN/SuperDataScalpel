package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialServiceInputResourceSelection;
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Compiles an ArcGIS REST or WFS feature resource as a bounded batch input. */
public final class SpatialServiceInputNodeOperator implements CanvasNodeOperator {
    @Override public CanvasNodeType nodeType() { return CanvasNodeType.SPATIAL_SERVICE_INPUT; }
    @Override public CanvasNodeCategory category() { return CanvasNodeCategory.INPUT; }
    @Override public Set<CanvasExecutionMode> supportedModes() { return Set.of(CanvasExecutionMode.BATCH); }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition, Map<String, SparkCanvasTable> inputs, CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof SpatialServiceInputNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_SERVICE_INPUT operator received " + definition.nodeType());
        }
        SpatialServiceInputConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(List.of());
        CanvasNodeIssueSink issues = context.issues();
        UUID dataSourceId = CanvasNodeSupport.parseUuid(configuration.dataSourceId(), "configuration.dataSourceId", issues);
        if (dataSourceId == null) return CanvasNodeOperationResult.invalid(List.of());
        MetadataIndex.DataSourceEntry dataSource = context.metadataIndex().dataSource(dataSourceId);
        if (dataSource == null || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.HTTP_API
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.SOURCE)) {
            issues.error("DATA_SOURCE_UNAVAILABLE", "空间服务数据源不存在、未启用或不具有 SOURCE 用途", "configuration.dataSourceId");
            return CanvasNodeOperationResult.invalid(List.of());
        }
        if (configuration.resources().isEmpty()) {
            issues.error("SPATIAL_RESOURCE_REQUIRED", "至少选择一个空间要素资源", "configuration.resources");
        }
        List<ResolvedResource> resolved = new java.util.ArrayList<>();
        Set<UUID> resourceIds = new java.util.HashSet<>();
        Set<String> outputNames = new java.util.HashSet<>();
        for (int index = 0; index < configuration.resources().size(); index++) {
            SpatialServiceInputResourceSelection selection = configuration.resources().get(index);
            String path = "configuration.resources[" + index + "]";
            if (selection == null) {
                issues.error("SPATIAL_RESOURCE_REQUIRED", "空间要素资源不能为空", path);
                continue;
            }
            UUID resourceId = CanvasNodeSupport.parseUuid(selection.resourceId(), path + ".resourceId", issues);
            CanvasNodeSupport.required(selection.outputTableName(), "空间服务输出表名不能为空", path + ".outputTableName", issues);
            if (resourceId == null) continue;
            if (!resourceIds.add(resourceId)) {
                issues.error("DUPLICATE_SPATIAL_RESOURCE_SELECTION", "空间要素资源重复", path + ".resourceId");
                continue;
            }
            if (!selection.outputTableName().isBlank() && !outputNames.add(selection.outputTableName())) {
                issues.error("DUPLICATE_TABLE_NAME", "空间服务输出表名重复：" + selection.outputTableName(), path + ".outputTableName");
            }
            MetadataTable resource = dataSource.table(resourceId.toString());
            if (resource == null || resource.objectType() != DatabaseObjectType.SPATIAL_FEATURE_RESOURCE) {
                issues.error("SPATIAL_RESOURCE_NOT_FOUND", "空间要素资源不存在", path + ".resourceId");
                continue;
            }
            CanvasNodeSupport.validateSupportedGeometry(resource.columns(), path + ".resourceId", issues);
            resolved.add(new ResolvedResource(selection, resourceId, resource));
        }
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(List.of());
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>();
        List<CanvasTableSchema> schemas = new java.util.ArrayList<>();
        for (ResolvedResource item : resolved) {
            CanvasTableSchema schema = new CanvasTableSchema(item.selection().outputTableName(),
                    CanvasTableOrigin.spatialService(dataSourceId, item.resourceId()), item.resource().columns());
            Dataset<Row> dataset = context.dataAccess().readSpatialServiceInput(node, item.selection(), schema);
            output.put(schema.name(), new SparkCanvasTable(schema, dataset));
            schemas.add(schema);
        }
        return CanvasNodeOperationResult.propagated(output, schemas);
    }

    private record ResolvedResource(
            SpatialServiceInputResourceSelection selection, UUID resourceId, MetadataTable resource
    ) {
    }
}

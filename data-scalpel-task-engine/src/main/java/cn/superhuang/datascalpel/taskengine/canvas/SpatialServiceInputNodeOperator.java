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
import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
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
        UUID resourceId = CanvasNodeSupport.parseUuid(configuration.resourceId(), "configuration.resourceId", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "空间服务输出表名不能为空", "configuration.outputTableName", issues);
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(List.of());
        MetadataIndex.DataSourceEntry dataSource = context.metadataIndex().dataSource(dataSourceId);
        if (dataSource == null || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.HTTP_API
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.SOURCE)) {
            issues.error("DATA_SOURCE_UNAVAILABLE", "空间服务数据源不存在、未启用或不具有 SOURCE 用途", "configuration.dataSourceId");
            return CanvasNodeOperationResult.invalid(List.of());
        }
        MetadataTable resource = dataSource.table(resourceId.toString());
        if (resource == null || resource.objectType() != DatabaseObjectType.SPATIAL_FEATURE_RESOURCE) {
            issues.error("SPATIAL_RESOURCE_NOT_FOUND", "空间要素资源不存在", "configuration.resourceId");
            return CanvasNodeOperationResult.invalid(List.of());
        }
        CanvasNodeSupport.validateSupportedGeometry(resource.columns(), "configuration.resourceId", issues);
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(List.of());
        CanvasTableSchema schema = new CanvasTableSchema(configuration.outputTableName(),
                CanvasTableOrigin.spatialService(dataSourceId, resourceId), resource.columns());
        Dataset<Row> dataset = context.dataAccess().readSpatialServiceInput(node, schema);
        return CanvasNodeOperationResult.propagated(Map.of(schema.name(), new SparkCanvasTable(schema, dataset)), List.of(schema));
    }
}

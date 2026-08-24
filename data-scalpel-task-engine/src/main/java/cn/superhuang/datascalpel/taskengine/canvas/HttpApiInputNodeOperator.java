package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.datascalpel.taskengine.compiler.MetadataIndex;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableOrigin;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.ConnectionKind;
import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.data.scalpel.contract.task.DatabaseObjectType;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputConfiguration;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.HttpApiInputResourceSelection;
import cn.superhuang.data.scalpel.contract.task.MetadataTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class HttpApiInputNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.HTTP_API_INPUT;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.INPUT;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof HttpApiInputNodeDefinition node)) {
            throw new IllegalArgumentException("HTTP_API_INPUT operator received " + definition.nodeType());
        }
        HttpApiInputConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(List.of());
        }
        CanvasNodeIssueSink issues = context.issues();
        UUID dataSourceId = CanvasNodeSupport.parseUuid(
                configuration.dataSourceId(), "configuration.dataSourceId", issues);
        if (dataSourceId == null) {
            return CanvasNodeOperationResult.invalid(List.of());
        }
        MetadataIndex.DataSourceEntry dataSource = context.metadataIndex().dataSource(dataSourceId);
        if (dataSource == null
                || !dataSource.metadata().enabled()
                || dataSource.metadata().connectionKind() != ConnectionKind.HTTP_API
                || !dataSource.metadata().purposes().contains(DataSourcePurpose.SOURCE)) {
            issues.error(
                    "DATA_SOURCE_UNAVAILABLE",
                    "API 数据源不存在、未启用或不具有 SOURCE 用途",
                    "configuration.dataSourceId"
            );
            return CanvasNodeOperationResult.invalid(List.of());
        }
        if (configuration.resources().isEmpty()) {
            issues.error("API_RESOURCE_REQUIRED", "至少选择一个 API 资源", "configuration.resources");
        }
        List<ResolvedResource> resolved = new java.util.ArrayList<>();
        Set<UUID> resourceIds = new java.util.HashSet<>();
        Set<String> outputNames = new java.util.HashSet<>();
        for (int index = 0; index < configuration.resources().size(); index++) {
            HttpApiInputResourceSelection selection = configuration.resources().get(index);
            String path = "configuration.resources[" + index + "]";
            if (selection == null) {
                issues.error("API_RESOURCE_REQUIRED", "API 资源不能为空", path);
                continue;
            }
            UUID resourceId = CanvasNodeSupport.parseUuid(selection.resourceId(), path + ".resourceId", issues);
            CanvasNodeSupport.required(selection.outputTableName(), "API 输出表名不能为空", path + ".outputTableName", issues);
            if (resourceId == null) continue;
            if (!resourceIds.add(resourceId)) {
                issues.error("DUPLICATE_API_RESOURCE_SELECTION", "API 资源重复", path + ".resourceId");
                continue;
            }
            if (!selection.outputTableName().isBlank() && !outputNames.add(selection.outputTableName())) {
                issues.error("DUPLICATE_TABLE_NAME", "API 输出表名重复：" + selection.outputTableName(), path + ".outputTableName");
            }
            MetadataTable resource = dataSource.table(resourceId.toString());
            if (resource == null || resource.objectType() != DatabaseObjectType.API_RESOURCE) {
                issues.error("API_RESOURCE_NOT_FOUND", "API 资源不存在", path + ".resourceId");
                continue;
            }
            resolved.add(new ResolvedResource(selection, resourceId, resource));
        }
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(List.of());
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>();
        List<CanvasTableSchema> schemas = new java.util.ArrayList<>();
        for (ResolvedResource item : resolved) {
            CanvasTableSchema schema = new CanvasTableSchema(
                    item.selection().outputTableName(),
                    CanvasTableOrigin.httpApi(dataSourceId, item.resourceId()),
                    item.resource().columns());
            Dataset<Row> dataset = context.dataAccess().readHttpApiInput(node, item.selection(), schema);
            output.put(schema.name(), new SparkCanvasTable(schema, dataset));
            schemas.add(schema);
        }
        return CanvasNodeOperationResult.propagated(output, schemas);
    }

    private record ResolvedResource(
            HttpApiInputResourceSelection selection, UUID resourceId, MetadataTable resource
    ) {
    }
}

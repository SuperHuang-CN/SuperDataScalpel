package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialClipConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialClipNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SpatialClipNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_CLIP;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.PROCESSOR;
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
        if (!(definition instanceof SpatialClipNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "SPATIAL_CLIP operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialClipConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.maskTableName(), "请选择 Mask 表",
                "configuration.maskTableName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        CanvasNodeSupport.required(configuration.sourceGeometryColumnName(), "请选择来源 Geometry 字段",
                "configuration.sourceGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.maskGeometryColumnName(), "请选择 Mask Geometry 字段",
                "configuration.maskGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.outputColumnName(), "请输入裁剪结果字段名",
                "configuration.outputColumnName", issues);
        if (!CanvasNodeSupport.blank(configuration.sourceTableName())
                && configuration.sourceTableName().equals(configuration.maskTableName())) {
            issues.error("INVALID_SPATIAL_CLIP_TABLE", "来源表与 Mask 表不能相同",
                    "configuration.maskTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }

        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null : inputs.get(configuration.sourceTableName());
        SparkCanvasTable mask = CanvasNodeSupport.blank(configuration.maskTableName())
                ? null : inputs.get(configuration.maskTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.maskTableName()) && mask == null) {
            issues.error("TABLE_NOT_FOUND",
                    "Mask 表不在上游数据中：" + configuration.maskTableName(),
                    "configuration.maskTableName");
        }
        validateBounded(source, "configuration.sourceTableName", issues);
        validateBounded(mask, "configuration.maskTableName", issues);

        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        Map<String, CanvasColumnSchema> maskColumns = mask == null
                ? Map.of() : CanvasNodeSupport.columns(mask.schema());
        CanvasColumnSchema sourceGeometry = validateGeometryColumn(
                source,
                sourceColumns,
                configuration.sourceGeometryColumnName(),
                "来源 Geometry 字段",
                "configuration.sourceGeometryColumnName",
                issues
        );
        CanvasColumnSchema maskGeometry = validateGeometryColumn(
                mask,
                maskColumns,
                configuration.maskGeometryColumnName(),
                "Mask Geometry 字段",
                "configuration.maskGeometryColumnName",
                issues
        );
        if (maskGeometry != null && maskGeometry.geometry() != null
                && maskGeometry.geometry().kind() != GeometryKind.POLYGON
                && maskGeometry.geometry().kind() != GeometryKind.MULTIPOLYGON) {
            issues.error(
                    "SPATIAL_CLIP_MASK_KIND_UNSUPPORTED",
                    "Mask Geometry 只支持 Polygon 或 MultiPolygon",
                    "configuration.maskGeometryColumnName"
            );
        }
        if (sourceGeometry != null && sourceGeometry.geometry() != null
                && maskGeometry != null && maskGeometry.geometry() != null) {
            GeometryTypeDefinition sourceType = sourceGeometry.geometry();
            GeometryTypeDefinition maskType = maskGeometry.geometry();
            if (sourceType.dimension() != maskType.dimension()) {
                issues.error(
                        "SPATIAL_CLIP_DIMENSION_MISMATCH",
                        "来源与 Mask Geometry 坐标维度不一致",
                        "configuration.maskGeometryColumnName"
                );
            }
            if (!sourceType.crs().equals(maskType.crs())) {
                issues.error(
                        "SPATIAL_CLIP_CRS_MISMATCH",
                        "来源与 Mask Geometry CRS 不一致，请先使用空间转换节点",
                        "configuration.maskGeometryColumnName"
                );
            }
        }
        Set<String> outputNames = new HashSet<>(sourceColumns.keySet());
        if (!CanvasNodeSupport.blank(configuration.outputColumnName())
                && !outputNames.add(configuration.outputColumnName())) {
            issues.error("DUPLICATE_COLUMN_NAME",
                    "输出字段名重复：" + configuration.outputColumnName(),
                    "configuration.outputColumnName");
        }
        if (source == null || mask == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        GeometryTypeDefinition sourceType = sourceGeometry.geometry();
        Dataset<Row> sourceDataset = source.dataset().alias("spatial_clip_source");
        Dataset<Row> maskDataset = mask.dataset().alias("spatial_clip_mask");
        Column sourceGeometryExpression = sourceDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.sourceGeometryColumnName()));
        Column maskGeometryExpression = maskDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.maskGeometryColumnName()));
        Dataset<Row> candidates = sourceDataset.join(
                maskDataset,
                st_predicates.ST_Intersects(sourceGeometryExpression, maskGeometryExpression),
                "inner"
        );
        Column clippedGeometry = st_functions.ST_SetSRID(
                st_functions.ST_Intersection(sourceGeometryExpression, maskGeometryExpression),
                functions.lit(sourceType.crs().code())
        ).alias(configuration.outputColumnName());
        List<Column> projection = new ArrayList<>(source.schema().columns().size() + 1);
        for (CanvasColumnSchema column : source.schema().columns()) {
            projection.add(sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name())));
        }
        projection.add(clippedGeometry);
        Dataset<Row> projected = candidates.select(projection.toArray(Column[]::new));
        Column resultGeometry = projected.col(
                CanvasNodeSupport.quoteIdentifier(configuration.outputColumnName()));
        Dataset<Row> clippedDataset = projected.filter(
                resultGeometry.isNotNull().and(
                        functions.not(st_functions.ST_IsEmpty(resultGeometry))));

        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        outputColumns.add(new CanvasColumnSchema(
                configuration.outputColumnName(), PlatformDataType.GEOMETRY,
                null, null, null, false,
                null, false, false, null,
                new GeometryTypeDefinition(
                        GeometryKind.GEOMETRY,
                        sourceType.crs(),
                        sourceType.dimension()
                )
        ));
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, clippedDataset));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateBounded(
            SparkCanvasTable table,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (table != null && table.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error(
                    "SPATIAL_CLIP_REQUIRES_BOUNDED_INPUT",
                    "空间裁剪只支持有界输入",
                    path
            );
        }
    }

    private static CanvasColumnSchema validateGeometryColumn(
            SparkCanvasTable table,
            Map<String, CanvasColumnSchema> columns,
            String columnName,
            String label,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (table == null || CanvasNodeSupport.blank(columnName)) {
            return null;
        }
        CanvasColumnSchema column = columns.get(columnName);
        if (column == null) {
            issues.error("COLUMN_NOT_FOUND", label + "不存在：" + columnName, path);
            return null;
        }
        if (column.fieldType() != PlatformDataType.GEOMETRY) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                    label + "不是 Geometry：" + columnName, path);
            return column;
        }
        CanvasNodeSupport.validateSupportedGeometry(List.of(column), path, issues);
        return column;
    }
}

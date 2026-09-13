package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialClipConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialClipNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageMetadata;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_aggregates;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SpatialClipNodeOperator implements CanvasNodeOperator {

    private static final String SOURCE_ALIAS = "spatial_clip_source";
    private static final String MASK_ALIAS = "spatial_clip_mask";
    private static final String MASK_UNION_ALIAS = "spatial_clip_mask_union";

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
            if (configuration.usesSourceFamily()
                    && SpatialOverlayGeometrySupport.family(sourceType.kind()) == 0) {
                issues.error(
                        "SPATIAL_CLIP_SOURCE_KIND_UNSUPPORTED",
                        "保持来源家族时只支持明确的 Point、LineString、Polygon 及对应 Multi 类型",
                        "configuration.sourceGeometryColumnName"
                );
            }
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
        GeometryTypeDefinition outputType = outputGeometryType(configuration, sourceType);
        Dataset<Row> sourceDataset;
        Dataset<Row> candidates;
        Column sourceGeometryExpression;
        Column maskGeometryExpression;
        if (configuration.dissolvesMasks()) {
            String rowId = internalName(source.dataset(), mask.dataset(),
                    "__datascalpel_clip_source_row_id");
            String maskUnion = internalName(source.dataset(), mask.dataset(),
                    "__datascalpel_clip_mask_union");
            sourceDataset = source.dataset().withColumn(rowId,
                    CatalystLineageMetadata.markTechnicalColumn(
                            functions.monotonically_increasing_id(), rowId))
                    .alias(SOURCE_ALIAS);
            Dataset<Row> maskDataset = mask.dataset().alias(MASK_ALIAS);
            Column sourceForMatch = qualified(SOURCE_ALIAS,
                    configuration.sourceGeometryColumnName());
            Column maskForMatch = qualified(MASK_ALIAS,
                    configuration.maskGeometryColumnName());
            if (configuration.usesSourceFamily()) {
                sourceForMatch = st_functions.ST_Force2D(sourceForMatch);
                maskForMatch = st_functions.ST_Force2D(maskForMatch);
            }
            Dataset<Row> matchedMasks = sourceDataset.join(
                            maskDataset,
                            st_predicates.ST_Intersects(sourceForMatch, maskForMatch),
                            "inner")
                    .groupBy(qualified(SOURCE_ALIAS, rowId))
                    .agg(st_aggregates.ST_Union_Agg(maskForMatch).alias(maskUnion))
                    .alias(MASK_UNION_ALIAS);
            candidates = sourceDataset.join(
                    matchedMasks,
                    qualified(SOURCE_ALIAS, rowId).eqNullSafe(
                            qualified(MASK_UNION_ALIAS, rowId)),
                    "inner");
            sourceGeometryExpression = qualified(SOURCE_ALIAS,
                    configuration.sourceGeometryColumnName());
            maskGeometryExpression = qualified(MASK_UNION_ALIAS, maskUnion);
            if (configuration.usesSourceFamily()) {
                sourceGeometryExpression = st_functions.ST_Force2D(sourceGeometryExpression);
            }
        } else {
            sourceDataset = source.dataset().alias(SOURCE_ALIAS);
            Dataset<Row> maskDataset = mask.dataset().alias(MASK_ALIAS);
            sourceGeometryExpression = qualified(SOURCE_ALIAS,
                    configuration.sourceGeometryColumnName());
            maskGeometryExpression = qualified(MASK_ALIAS,
                    configuration.maskGeometryColumnName());
            if (configuration.usesSourceFamily()) {
                sourceGeometryExpression = st_functions.ST_Force2D(sourceGeometryExpression);
                maskGeometryExpression = st_functions.ST_Force2D(maskGeometryExpression);
            }
            candidates = sourceDataset.join(
                    maskDataset,
                    st_predicates.ST_Intersects(sourceGeometryExpression, maskGeometryExpression),
                    "inner");
        }
        Column clippedGeometry = SpatialOverlayGeometrySupport.result(
                st_functions.ST_Intersection(sourceGeometryExpression, maskGeometryExpression),
                outputType
        ).alias(configuration.outputColumnName());
        List<Column> projection = new ArrayList<>(source.schema().columns().size() + 1);
        for (CanvasColumnSchema column : source.schema().columns()) {
            projection.add(qualified(SOURCE_ALIAS, column.name()));
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
                outputType
        ));
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, clippedDataset));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static GeometryTypeDefinition outputGeometryType(
            SpatialClipConfiguration configuration,
            GeometryTypeDefinition sourceType
    ) {
        if (!configuration.usesSourceFamily()) {
            return new GeometryTypeDefinition(
                    GeometryKind.GEOMETRY,
                    sourceType.crs(),
                    sourceType.dimension()
            );
        }
        GeometryKind outputKind = switch (SpatialOverlayGeometrySupport.family(sourceType.kind())) {
            case 1 -> GeometryKind.MULTIPOINT;
            case 2 -> GeometryKind.MULTILINESTRING;
            case 3 -> GeometryKind.MULTIPOLYGON;
            default -> throw new IllegalStateException(
                    "Spatial Clip source family validation must precede planning");
        };
        return new GeometryTypeDefinition(outputKind, sourceType.crs(), CoordinateDimension.XY);
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

    private static Column qualified(String alias, String name) {
        return functions.col(alias + "." + CanvasNodeSupport.quoteIdentifier(name));
    }

    private static String internalName(Dataset<Row> left, Dataset<Row> right, String base) {
        Set<String> occupied = new HashSet<>();
        occupied.addAll(List.of(left.columns()));
        occupied.addAll(List.of(right.columns()));
        String result = base;
        int suffix = 1;
        while (occupied.contains(result)) result = base + "_" + suffix++;
        return result;
    }
}

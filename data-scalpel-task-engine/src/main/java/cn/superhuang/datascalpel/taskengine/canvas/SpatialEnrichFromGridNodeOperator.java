package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialEnrichFromGridConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialEnrichFromGridField;
import cn.superhuang.data.scalpel.contract.task.SpatialEnrichFromGridNodeDefinition;
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
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** ArcGIS-style enrichment of bounded Point rows from an existing multi-variable polygon grid. */
public final class SpatialEnrichFromGridNodeOperator implements CanvasNodeOperator {
    private static final String POINT_ALIAS = "enrich_grid_point";
    private static final String GRID_ALIAS = "enrich_grid_cell";
    private static final String MATCH_ALIAS = "enrich_grid_match";

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_ENRICH_FROM_GRID;
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
        if (!(definition instanceof SpatialEnrichFromGridNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_ENRICH_FROM_GRID operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialEnrichFromGridConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        CanvasNodeIssueSink issues = context.issues();
        validateBase(configuration, inputs, issues);
        SparkCanvasTable points = inputs.get(configuration.pointTableName());
        SparkCanvasTable grid = inputs.get(configuration.gridTableName());
        if (!CanvasNodeSupport.blank(configuration.pointTableName()) && points == null) {
            issues.error("TABLE_NOT_FOUND", "Point 表不在上游数据中：" + configuration.pointTableName(),
                    "configuration.pointTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.gridTableName()) && grid == null) {
            issues.error("TABLE_NOT_FOUND", "多变量格网表不在上游数据中：" + configuration.gridTableName(),
                    "configuration.gridTableName");
        }
        if (points == null || grid == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        Validation validation = validateSchemas(configuration, points, grid, issues);
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);

        Dataset<Row> enriched = buildPlan(configuration, points, grid, validation.fields());
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null,
                outputColumns(points.schema(), validation.fields()),
                CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, enriched));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateBase(
            SpatialEnrichFromGridConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.pointTableName(), "请选择 Point 表",
                "configuration.pointTableName", issues);
        CanvasNodeSupport.required(configuration.pointGeometryColumnName(), "请选择 Point Geometry 字段",
                "configuration.pointGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.gridTableName(), "请选择多变量格网表",
                "configuration.gridTableName", issues);
        CanvasNodeSupport.required(configuration.gridGeometryColumnName(), "请选择格网 Geometry 字段",
                "configuration.gridGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.gridIdColumnName(), "请选择格网唯一标识字段",
                "configuration.gridIdColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        if (!CanvasNodeSupport.blank(configuration.pointTableName())
                && configuration.pointTableName().equals(configuration.gridTableName())) {
            issues.error("INVALID_SPATIAL_ENRICH_GRID_TABLES", "Point 表和多变量格网表不能相同",
                    "configuration.gridTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }
        if (configuration.enrichFields() == null) {
            issues.error("INVALID_SPATIAL_ENRICH_FIELDS", "丰富字段必须是数组", "configuration.enrichFields");
        } else if (configuration.enrichFields().isEmpty()) {
            issues.error("EMPTY_SPATIAL_ENRICH_FIELDS", "至少选择一个格网属性字段",
                    "configuration.enrichFields");
        }
    }

    private static Validation validateSchemas(
            SpatialEnrichFromGridConfiguration configuration,
            SparkCanvasTable points,
            SparkCanvasTable grid,
            CanvasNodeIssueSink issues
    ) {
        if (points.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "格网丰富只支持有界 Point 表",
                    "configuration.pointTableName");
        }
        if (grid.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "格网丰富只支持有界多变量格网表",
                    "configuration.gridTableName");
        }
        Map<String, CanvasColumnSchema> pointColumns = CanvasNodeSupport.columns(points.schema());
        Map<String, CanvasColumnSchema> gridColumns = CanvasNodeSupport.columns(grid.schema());
        CanvasColumnSchema pointGeometry = findColumn(
                pointColumns, configuration.pointGeometryColumnName(), "Point Geometry",
                "configuration.pointGeometryColumnName", issues);
        CanvasColumnSchema gridGeometry = findColumn(
                gridColumns, configuration.gridGeometryColumnName(), "格网 Geometry",
                "configuration.gridGeometryColumnName", issues);
        if (pointGeometry != null && !isPoint(pointGeometry.geometry())) {
            issues.error("SPATIAL_ENRICH_POINT_GEOMETRY_REQUIRED",
                    "来源必须使用带完整元数据的 XY Point Geometry",
                    "configuration.pointGeometryColumnName");
        }
        if (gridGeometry != null && !isPolygon(gridGeometry.geometry())) {
            issues.error("SPATIAL_ENRICH_GRID_GEOMETRY_REQUIRED",
                    "多变量格网必须使用带完整元数据的 XY Polygon Geometry",
                    "configuration.gridGeometryColumnName");
        }
        if (pointGeometry != null && gridGeometry != null
                && isPoint(pointGeometry.geometry()) && isPolygon(gridGeometry.geometry())
                && !pointGeometry.geometry().crs().equals(gridGeometry.geometry().crs())) {
            issues.error("SPATIAL_ENRICH_GRID_CRS_MISMATCH",
                    "Point 表和多变量格网表必须使用完全相同的 CRS，请先显式空间转换",
                    "configuration.gridGeometryColumnName");
        }

        CanvasColumnSchema gridId = findColumn(
                gridColumns, configuration.gridIdColumnName(), "格网唯一标识",
                "configuration.gridIdColumnName", issues);
        if (gridId != null && gridId.fieldType() == PlatformDataType.GEOMETRY) {
            issues.error("SCALAR_COLUMN_REQUIRED", "格网唯一标识必须是非 Geometry 标量字段",
                    "configuration.gridIdColumnName");
        }

        List<ResolvedField> fields = new ArrayList<>();
        Set<String> sources = new HashSet<>();
        Set<String> outputs = new HashSet<>();
        pointColumns.keySet().forEach(name -> outputs.add(name.toLowerCase(Locale.ROOT)));
        if (configuration.enrichFields() != null) {
            for (int index = 0; index < configuration.enrichFields().size(); index++) {
                SpatialEnrichFromGridField field = configuration.enrichFields().get(index);
                String path = "configuration.enrichFields[" + index + "]";
                if (field == null) {
                    issues.error("INVALID_SPATIAL_ENRICH_FIELD", "丰富字段配置不能为空", path);
                    continue;
                }
                CanvasNodeSupport.required(field.sourceColumnName(), "请选择格网来源字段",
                        path + ".sourceColumnName", issues);
                CanvasNodeSupport.required(field.outputColumnName(), "请输入结果字段名",
                        path + ".outputColumnName", issues);
                if (!CanvasNodeSupport.blank(field.sourceColumnName())
                        && !sources.add(field.sourceColumnName().toLowerCase(Locale.ROOT))) {
                    issues.error("DUPLICATE_SPATIAL_ENRICH_SOURCE_COLUMN",
                            "格网来源字段重复：" + field.sourceColumnName(), path + ".sourceColumnName");
                }
                CanvasColumnSchema source = CanvasNodeSupport.blank(field.sourceColumnName())
                        ? null : gridColumns.get(field.sourceColumnName());
                if (!CanvasNodeSupport.blank(field.sourceColumnName()) && source == null) {
                    issues.error("COLUMN_NOT_FOUND", "格网字段不存在：" + field.sourceColumnName(),
                            path + ".sourceColumnName");
                } else if (source != null && source.fieldType() == PlatformDataType.GEOMETRY) {
                    issues.error("SCALAR_COLUMN_REQUIRED", "只能回填非 Geometry 标量字段",
                            path + ".sourceColumnName");
                }
                if (!CanvasNodeSupport.blank(field.outputColumnName())
                        && !outputs.add(field.outputColumnName().toLowerCase(Locale.ROOT))) {
                    issues.error("DUPLICATE_COLUMN_NAME", "结果字段名重复或与 Point 原字段冲突："
                            + field.outputColumnName(), path + ".outputColumnName");
                }
                if (source != null && source.fieldType() != PlatformDataType.GEOMETRY) {
                    fields.add(new ResolvedField(field, source));
                }
            }
        }
        return new Validation(List.copyOf(fields));
    }

    private static CanvasColumnSchema findColumn(
            Map<String, CanvasColumnSchema> columns,
            String name,
            String label,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(name)) return null;
        CanvasColumnSchema column = columns.get(name);
        if (column == null) issues.error("COLUMN_NOT_FOUND", label + "字段不存在：" + name, path);
        return column;
    }

    private static boolean isPoint(GeometryTypeDefinition geometry) {
        return geometry != null && geometry.kind() == GeometryKind.POINT
                && geometry.dimension() == CoordinateDimension.XY && geometry.crs() != null;
    }

    private static boolean isPolygon(GeometryTypeDefinition geometry) {
        return geometry != null
                && (geometry.kind() == GeometryKind.POLYGON || geometry.kind() == GeometryKind.MULTIPOLYGON)
                && geometry.dimension() == CoordinateDimension.XY && geometry.crs() != null;
    }

    private static Dataset<Row> buildPlan(
            SpatialEnrichFromGridConfiguration configuration,
            SparkCanvasTable points,
            SparkCanvasTable grid,
            List<ResolvedField> fields
    ) {
        Set<String> internalNames = new HashSet<>();
        internalNames.addAll(CanvasNodeSupport.columns(points.schema()).keySet());
        internalNames.addAll(CanvasNodeSupport.columns(grid.schema()).keySet());
        String pointRowId = CanvasSortSupport.temporaryColumnName(
                internalNames, "__datascalpel_enrich_grid_point_row_id");
        internalNames.add(pointRowId);
        String gridOrderId = CanvasSortSupport.temporaryColumnName(
                internalNames, "__datascalpel_enrich_grid_order_id");
        internalNames.add(gridOrderId);
        String rank = CanvasSortSupport.temporaryColumnName(
                internalNames, "__datascalpel_enrich_grid_rank");

        Dataset<Row> pointDataset = points.dataset()
                .withColumn(pointRowId, CatalystLineageMetadata.markTechnicalColumn(
                        functions.monotonically_increasing_id(), pointRowId))
                .alias(POINT_ALIAS);
        Dataset<Row> gridDataset = grid.dataset().alias(GRID_ALIAS);
        Column pointGeometry = pointDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.pointGeometryColumnName()));
        Column gridGeometry = gridDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.gridGeometryColumnName()));
        // Sedona cannot optimize a spatial LEFT OUTER JOIN into an indexed spatial join.
        // Find actual matches with an INNER JOIN, select one grid cell per point, then
        // restore unmatched points through an equality LEFT JOIN on a plan-local row ID.
        Dataset<Row> joined = pointDataset.join(
                gridDataset,
                st_predicates.ST_Intersects(pointGeometry, gridGeometry),
                "inner");
        Column rawGridId = gridDataset.col(CanvasNodeSupport.quoteIdentifier(configuration.gridIdColumnName()));
        Column checkedGridId = functions.when(
                        gridGeometry.isNotNull().and(rawGridId.isNull()),
                        functions.raise_error(functions.lit("SPATIAL_ENRICH_GRID_ID_NULL")).cast("string"))
                .otherwise(rawGridId.cast("string"));
        Dataset<Row> checked = joined.withColumn(gridOrderId, checkedGridId);

        List<Column> ordering = new ArrayList<>();
        ordering.add(checked.col(CanvasNodeSupport.quoteIdentifier(gridOrderId)).asc_nulls_last());
        ordering.add(functions.sha2(st_functions.ST_AsBinary(gridGeometry), 256).asc_nulls_last());
        fields.forEach(field -> ordering.add(gridDataset.col(
                CanvasNodeSupport.quoteIdentifier(field.field().sourceColumnName()))
                .cast("string").asc_nulls_last()));
        WindowSpec oneCellPerPoint = Window.partitionBy(
                pointDataset.col(CanvasNodeSupport.quoteIdentifier(pointRowId)))
                .orderBy(ordering.toArray(Column[]::new));
        Dataset<Row> ranked = checked.withColumn(rank, functions.row_number().over(oneCellPerPoint))
                .filter(functions.col(CanvasNodeSupport.quoteIdentifier(rank)).equalTo(1));

        List<Column> matchProjection = new ArrayList<>();
        matchProjection.add(pointDataset.col(CanvasNodeSupport.quoteIdentifier(pointRowId)));
        for (ResolvedField field : fields) {
            matchProjection.add(gridDataset.col(CanvasNodeSupport.quoteIdentifier(
                            field.field().sourceColumnName()))
                    .alias(field.field().outputColumnName()));
        }
        Dataset<Row> matches = ranked.select(matchProjection.toArray(Column[]::new))
                .alias(MATCH_ALIAS);
        Dataset<Row> restored = pointDataset.join(
                matches,
                pointDataset.col(CanvasNodeSupport.quoteIdentifier(pointRowId)).eqNullSafe(
                        matches.col(CanvasNodeSupport.quoteIdentifier(pointRowId))),
                "left_outer");

        List<Column> projection = new ArrayList<>();
        for (CanvasColumnSchema column : points.schema().columns()) {
            projection.add(pointDataset.col(CanvasNodeSupport.quoteIdentifier(column.name())));
        }
        for (ResolvedField field : fields) {
            projection.add(matches.col(CanvasNodeSupport.quoteIdentifier(field.field().outputColumnName())));
        }
        return restored.select(projection.toArray(Column[]::new));
    }

    private static List<CanvasColumnSchema> outputColumns(
            CanvasTableSchema points,
            List<ResolvedField> fields
    ) {
        List<CanvasColumnSchema> result = new ArrayList<>(points.columns());
        for (ResolvedField field : fields) {
            CanvasColumnSchema source = field.source();
            result.add(new CanvasColumnSchema(
                    field.field().outputColumnName(), source.fieldType(), source.length(),
                    source.precision(), source.scale(), true, null, false, false,
                    source.comment(), null));
        }
        return List.copyOf(result);
    }

    private record ResolvedField(SpatialEnrichFromGridField field, CanvasColumnSchema source) {
    }

    private record Validation(List<ResolvedField> fields) {
    }
}

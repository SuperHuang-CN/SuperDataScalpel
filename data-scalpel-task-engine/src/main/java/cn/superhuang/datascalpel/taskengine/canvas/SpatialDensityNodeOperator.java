package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialBinShape;
import cn.superhuang.data.scalpel.contract.task.SpatialDensityBinShape;
import cn.superhuang.data.scalpel.contract.task.SpatialDensityConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialDensityField;
import cn.superhuang.data.scalpel.contract.task.SpatialDensityNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialDensityWeighting;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialTemporalSlicing;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Deterministic projected point-density grid. The plan remains lazy during compilation. */
public final class SpatialDensityNodeOperator implements CanvasNodeOperator {

    private static final String Q = "__datascalpel_density_q";
    private static final String R = "__datascalpel_density_r";
    private static final String X = "__datascalpel_density_x";
    private static final String Y = "__datascalpel_density_y";
    private static final String CENTER_X = "__datascalpel_density_center_x";
    private static final String CENTER_Y = "__datascalpel_density_center_y";
    private static final String DISTANCE_SQUARED = "__datascalpel_density_distance_squared";
    private static final String KERNEL = "__datascalpel_density_kernel";
    private static final String WINDOW_START = "__datascalpel_density_window_start";
    private static final String WINDOW_END = "__datascalpel_density_window_end";

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_DENSITY;
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
        if (!(definition instanceof SpatialDensityNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_DENSITY operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialDensityConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        CanvasNodeIssueSink issues = context.issues();
        validateBase(configuration, inputs, issues);
        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }
        if (source == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        Map<String, CanvasColumnSchema> columns = CanvasNodeSupport.columns(source.schema());
        CanvasColumnSchema point = validatePoint(source, configuration, columns, issues);
        List<ResolvedField> fields = validateFields(configuration.fields(), columns, issues);
        SpatialTemporalSupport.WindowParameters window = validateTemporal(
                configuration.temporalSlicing(), columns, issues);
        validateOutputNames(configuration, issues);

        SpatialDistanceSupport.Resolution bin = point == null || point.geometry() == null ? null
                : SpatialDistanceSupport.resolve(configuration.binSize(), configuration.binSizeUnit(), point.geometry().crs());
        SpatialDistanceSupport.Resolution radius = point == null || point.geometry() == null ? null
                : SpatialDistanceSupport.resolve(configuration.radius(), configuration.radiusUnit(), point.geometry().crs());
        SpatialDistanceSupport.Resolution unitsPerMetre = point == null || point.geometry() == null ? null
                : SpatialDistanceSupport.resolve(1d, SpatialDistanceUnit.METERS, point.geometry().crs());
        validateResolvedDistances(configuration, bin, radius, unitsPerMetre, issues);
        double squareMetres = SpatialDistanceSupport.squareMetresPerConfiguredUnit(configuration.areaUnit());
        if (configuration.areaUnit() == null || !Double.isFinite(squareMetres) || squareMetres <= 0) {
            issues.error("INVALID_SPATIAL_DENSITY_AREA_UNIT", "请选择有效的密度面积单位", "configuration.areaUnit");
        }
        if (issues.hasErrors() || point == null || bin == null || radius == null || unitsPerMetre == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        double side = configuration.binShape() == SpatialDensityBinShape.HEXAGON
                ? bin.sourceCrsValue() / Math.sqrt(3d) : bin.sourceCrsValue();
        double radiusValue = radius.sourceCrsValue();
        double areaFactor = unitsPerMetre.sourceCrsValue() * unitsPerMetre.sourceCrsValue() * squareMetres;
        if (!Double.isFinite(side) || side <= 0 || !Double.isFinite(areaFactor) || areaFactor <= 0) {
            issues.error("INVALID_SPATIAL_DENSITY_DISTANCE", "格网、半径或面积单位换算结果超出可计算范围", "configuration.binSize");
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> result = buildPlan(source.dataset(), configuration, fields, window, side,
                radiusValue, areaFactor, point.geometry().crs().code());
        List<CanvasColumnSchema> outputColumns = outputSchema(configuration, fields, point);
        CanvasTableSchema outputSchema = new CanvasTableSchema(configuration.outputTableName(), null,
                outputColumns, CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static Dataset<Row> buildPlan(
            Dataset<Row> source,
            SpatialDensityConfiguration configuration,
            List<ResolvedField> fields,
            SpatialTemporalSupport.WindowParameters window,
            double side,
            double radius,
            double areaFactor,
            int srid
    ) {
        Column geometry = source.col(CanvasNodeSupport.quoteIdentifier(configuration.pointGeometryColumnName()));
        Column x = st_functions.ST_X(geometry);
        Column y = st_functions.ST_Y(geometry);
        List<Column> projection = new ArrayList<>();
        projection.add(geometry.alias("__datascalpel_density_geometry"));
        projection.add(x.alias(X));
        projection.add(y.alias(Y));
        for (int index = 0; index < fields.size(); index++) {
            projection.add(source.col(CanvasNodeSupport.quoteIdentifier(fields.get(index).field().sourceColumnName()))
                    .cast("double").alias(valueColumn(index)));
        }
        if (configuration.temporalSlicing() != null) {
            projection.add(source.col(CanvasNodeSupport.quoteIdentifier(
                    configuration.temporalSlicing().timeColumnName())).alias("__datascalpel_density_time"));
        }
        Dataset<Row> points = source.select(projection.toArray(Column[]::new))
                .filter(functions.col("__datascalpel_density_geometry").isNotNull()
                        .and(st_functions.ST_IsEmpty(functions.col("__datascalpel_density_geometry")).equalTo(false)));
        if (configuration.temporalSlicing() != null && window != null) {
            points = SpatialTemporalSupport.addWindows(points, points.col("__datascalpel_density_time"),
                    window, WINDOW_START, WINDOW_END);
        }

        SpatialBinShape shape = shape(configuration.binShape());
        Column relativeX = PlanarGridSupport.checkedCoordinate(points.col(X), side);
        Column relativeY = PlanarGridSupport.checkedCoordinate(points.col(Y), side);
        PlanarGridSupport.Cell home = PlanarGridSupport.pointIndices(relativeX, relativeY, side, shape);
        long range = candidateRange(configuration.binShape(), radius, side);
        points = points
                .withColumn(Q, functions.explode(functions.sequence(home.q().minus(range), home.q().plus(range))))
                .withColumn(R, functions.explode(functions.sequence(home.r().minus(range), home.r().plus(range))));

        Column centerX;
        Column centerY;
        if (configuration.binShape() == SpatialDensityBinShape.SQUARE) {
            centerX = points.col(Q).multiply(side).plus(side / 2d);
            centerY = points.col(R).multiply(side).plus(side / 2d);
        } else {
            centerX = points.col(Q).multiply(1.5d * side);
            centerY = points.col(R).plus(points.col(Q).divide(2d)).multiply(Math.sqrt(3d) * side);
        }
        points = points.withColumn(CENTER_X, centerX).withColumn(CENTER_Y, centerY)
                .withColumn(DISTANCE_SQUARED,
                        functions.pow(points.col(X).minus(functions.col(CENTER_X)), 2d)
                                .plus(functions.pow(points.col(Y).minus(functions.col(CENTER_Y)), 2d)))
                .filter(functions.col(DISTANCE_SQUARED).leq(radius * radius));

        double normalizer = areaFactor / (Math.PI * radius * radius);
        Column kernel = configuration.weighting() == SpatialDensityWeighting.KERNEL
                ? functions.pow(functions.lit(1d).minus(functions.col(DISTANCE_SQUARED)
                        .divide(radius * radius)), 2d).multiply(3d * normalizer)
                : functions.when(points.col("__datascalpel_density_geometry").isNotNull(),
                        functions.lit(normalizer)).otherwise(functions.lit(0d));
        points = points.withColumn(KERNEL, kernel);

        List<Column> groups = new ArrayList<>(List.of(points.col(Q), points.col(R)));
        if (configuration.temporalSlicing() != null) {
            groups.add(points.col(WINDOW_START));
            groups.add(points.col(WINDOW_END));
        }
        List<Column> aggregations = new ArrayList<>();
        aggregations.add(functions.sum(points.col(KERNEL)).alias("__datascalpel_density_count"));
        for (int index = 0; index < fields.size(); index++) {
            Column value = points.col(valueColumn(index));
            Column checked = functions.when(value.isNull(), functions.lit(0d))
                    .when(functions.isnan(value).or(functions.abs(value).gt(Double.MAX_VALUE)),
                            functions.raise_error(functions.lit("SPATIAL_DENSITY_VALUE_NOT_FINITE")).cast("double"))
                    .otherwise(value);
            aggregations.add(functions.sum(checked.multiply(points.col(KERNEL))).alias(densityColumn(index)));
        }
        Dataset<Row> aggregate = points.groupBy(groups.toArray(Column[]::new)).agg(
                aggregations.getFirst(), aggregations.subList(1, aggregations.size()).toArray(Column[]::new));

        String identity = "DENSITY:" + configuration.binShape().name() + ":" + srid + ":"
                + Double.toHexString(configuration.binSize());
        List<Column> result = new ArrayList<>();
        result.add(functions.concat_ws(":", functions.lit(identity), aggregate.col(Q), aggregate.col(R))
                .alias(configuration.binIdColumnName()));
        result.add(PlanarGridSupport.geometry(aggregate.col(Q), aggregate.col(R), shape, side, srid, null)
                .alias(configuration.binGeometryColumnName()));
        SpatialTemporalSlicing temporal = configuration.temporalSlicing();
        if (temporal != null) {
            result.add(aggregate.col(WINDOW_START).alias(temporal.windowStartColumnName()));
            result.add(aggregate.col(WINDOW_END).alias(temporal.windowEndColumnName()));
        }
        result.add(aggregate.col("__datascalpel_density_count").alias(configuration.countDensityColumnName()));
        for (int index = 0; index < fields.size(); index++) {
            result.add(aggregate.col(densityColumn(index)).alias(fields.get(index).field().outputColumnName()));
        }
        return aggregate.select(result.toArray(Column[]::new));
    }

    private static void validateBase(
            SpatialDensityConfiguration c,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(c.sourceTableName(), "请选择来源表", "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(c.pointGeometryColumnName(), "请选择 Point Geometry 字段", "configuration.pointGeometryColumnName", issues);
        CanvasNodeSupport.required(c.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        CanvasNodeSupport.required(c.binIdColumnName(), "请输入格网 ID 字段名", "configuration.binIdColumnName", issues);
        CanvasNodeSupport.required(c.binGeometryColumnName(), "请输入格网 Geometry 字段名", "configuration.binGeometryColumnName", issues);
        CanvasNodeSupport.required(c.countDensityColumnName(), "请输入点数密度字段名", "configuration.countDensityColumnName", issues);
        if (c.weighting() == null) issues.error("REQUIRED_CONFIGURATION", "请选择密度计算方法", "configuration.weighting");
        if (c.binShape() == null) issues.error("REQUIRED_CONFIGURATION", "请选择格网形状", "configuration.binShape");
        if (!Double.isFinite(c.binSize()) || c.binSize() <= 0 || c.binSizeUnit() == null) {
            issues.error("INVALID_SPATIAL_DENSITY_BIN_SIZE", "格网大小必须是带单位的有限正数", "configuration.binSize");
        }
        if (!Double.isFinite(c.radius()) || c.radius() <= 0 || c.radiusUnit() == null) {
            issues.error("INVALID_SPATIAL_DENSITY_RADIUS", "搜索半径必须是带单位的有限正数", "configuration.radius");
        }
        if (!CanvasNodeSupport.blank(c.outputTableName()) && inputs.containsKey(c.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + c.outputTableName(), "configuration.outputTableName");
        }
        if (c.fields() == null) {
            issues.error("INVALID_SPATIAL_DENSITY_FIELDS", "数量字段数组不能为空", "configuration.fields");
        } else if (c.fields().size() > SpatialDensityConfiguration.MAX_FIELDS) {
            issues.error("SPATIAL_DENSITY_FIELD_COUNT_EXCEEDED", "数量字段不能超过 32 个", "configuration.fields");
        }
    }

    private static CanvasColumnSchema validatePoint(
            SparkCanvasTable source,
            SpatialDensityConfiguration c,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "空间密度只支持有界输入", "configuration.sourceTableName");
        }
        CanvasColumnSchema point = CanvasNodeSupport.blank(c.pointGeometryColumnName())
                ? null : columns.get(c.pointGeometryColumnName());
        if (!CanvasNodeSupport.blank(c.pointGeometryColumnName()) && point == null) {
            issues.error("COLUMN_NOT_FOUND", "Point Geometry 字段不存在：" + c.pointGeometryColumnName(),
                    "configuration.pointGeometryColumnName");
        } else if (point != null && (point.fieldType() != PlatformDataType.GEOMETRY || point.geometry() == null
                || point.geometry().kind() != GeometryKind.POINT
                || point.geometry().dimension() != CoordinateDimension.XY)) {
            issues.error("SPATIAL_POINT_XY_REQUIRED", "空间密度需要带完整元数据的 XY Point",
                    "configuration.pointGeometryColumnName");
        }
        return point;
    }

    private static List<ResolvedField> validateFields(
            List<SpatialDensityField> configured,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (configured == null) return List.of();
        Set<UUID> ids = new HashSet<>();
        Set<String> sources = new HashSet<>();
        List<ResolvedField> result = new ArrayList<>();
        for (int index = 0; index < configured.size(); index++) {
            SpatialDensityField field = configured.get(index);
            String path = "configuration.fields[" + index + "]";
            if (field == null) {
                issues.error("INVALID_SPATIAL_DENSITY_FIELD", "数量字段配置不能为空", path);
                continue;
            }
            try {
                if (!ids.add(UUID.fromString(field.fieldId()))) {
                    issues.error("DUPLICATE_SPATIAL_DENSITY_FIELD_ID", "数量字段 ID 重复", path + ".fieldId");
                }
            } catch (RuntimeException exception) {
                issues.error("INVALID_SPATIAL_DENSITY_FIELD_ID", "数量字段 ID 必须是 UUID", path + ".fieldId");
            }
            CanvasNodeSupport.required(field.sourceColumnName(), "请选择数值来源字段", path + ".sourceColumnName", issues);
            CanvasNodeSupport.required(field.outputColumnName(), "请输入密度输出字段名", path + ".outputColumnName", issues);
            if (!CanvasNodeSupport.blank(field.sourceColumnName())
                    && !sources.add(field.sourceColumnName().toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_SPATIAL_DENSITY_SOURCE_FIELD", "同一数值来源字段只能配置一次", path + ".sourceColumnName");
            }
            CanvasColumnSchema source = CanvasNodeSupport.blank(field.sourceColumnName())
                    ? null : columns.get(field.sourceColumnName());
            if (!CanvasNodeSupport.blank(field.sourceColumnName()) && source == null) {
                issues.error("COLUMN_NOT_FOUND", "数量字段不存在：" + field.sourceColumnName(), path + ".sourceColumnName");
            } else if (source != null && !numeric(source.fieldType())) {
                issues.error("NUMERIC_COLUMN_REQUIRED", "空间密度数量字段必须是数值类型", path + ".sourceColumnName");
            }
            result.add(new ResolvedField(field, source));
        }
        return List.copyOf(result);
    }

    private static SpatialTemporalSupport.WindowParameters validateTemporal(
            SpatialTemporalSlicing temporal,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        SpatialTemporalSupport.WindowParameters result = SpatialTemporalSupport.validateAndResolve(
                temporal, "configuration.temporalSlicing", issues);
        if (temporal != null && !CanvasNodeSupport.blank(temporal.timeColumnName())) {
            CanvasColumnSchema column = columns.get(temporal.timeColumnName());
            if (column == null) issues.error("COLUMN_NOT_FOUND", "时间字段不存在：" + temporal.timeColumnName(),
                    "configuration.temporalSlicing.timeColumnName");
            else if (column.fieldType() != PlatformDataType.TIMESTAMP) issues.error("TIMESTAMP_COLUMN_REQUIRED",
                    "时间切片字段必须是 TIMESTAMP", "configuration.temporalSlicing.timeColumnName");
        }
        return result;
    }

    private static void validateOutputNames(SpatialDensityConfiguration c, CanvasNodeIssueSink issues) {
        Set<String> names = new HashSet<>();
        addName(c.binIdColumnName(), "configuration.binIdColumnName", names, issues);
        addName(c.binGeometryColumnName(), "configuration.binGeometryColumnName", names, issues);
        addName(c.countDensityColumnName(), "configuration.countDensityColumnName", names, issues);
        if (c.temporalSlicing() != null) {
            addName(c.temporalSlicing().windowStartColumnName(), "configuration.temporalSlicing.windowStartColumnName", names, issues);
            addName(c.temporalSlicing().windowEndColumnName(), "configuration.temporalSlicing.windowEndColumnName", names, issues);
        }
        if (c.fields() != null) for (int index = 0; index < c.fields().size(); index++) {
            SpatialDensityField field = c.fields().get(index);
            if (field != null) addName(field.outputColumnName(), "configuration.fields[" + index + "].outputColumnName", names, issues);
        }
    }

    private static void addName(String name, String path, Set<String> names, CanvasNodeIssueSink issues) {
        if (!CanvasNodeSupport.blank(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
            issues.error("DUPLICATE_COLUMN_NAME", "输出字段名重复：" + name, path);
        }
    }

    private static void validateResolvedDistances(
            SpatialDensityConfiguration c,
            SpatialDistanceSupport.Resolution bin,
            SpatialDistanceSupport.Resolution radius,
            SpatialDistanceSupport.Resolution unitsPerMetre,
            CanvasNodeIssueSink issues
    ) {
        if (bin != null && (!bin.valid() || bin.angular())) {
            issues.error("SPATIAL_DENSITY_PROJECTED_CRS_REQUIRED",
                    bin.valid() ? "空间密度需要投影 CRS" : bin.error(), "configuration.binSizeUnit");
        }
        if (radius != null && (!radius.valid() || radius.angular())) {
            issues.error("SPATIAL_DENSITY_PROJECTED_CRS_REQUIRED",
                    radius.valid() ? "空间密度需要投影 CRS" : radius.error(), "configuration.radiusUnit");
        }
        if (unitsPerMetre != null && (!unitsPerMetre.valid() || unitsPerMetre.angular())) {
            issues.error("SPATIAL_DENSITY_PROJECTED_CRS_REQUIRED",
                    unitsPerMetre.valid() ? "密度面积换算需要投影 CRS" : unitsPerMetre.error(), "configuration.areaUnit");
        }
        if (bin == null || radius == null || !bin.valid() || !radius.valid()) return;
        if (radius.sourceCrsValue() <= bin.sourceCrsValue()) {
            issues.error("SPATIAL_DENSITY_RADIUS_TOO_SMALL", "搜索半径必须严格大于格网大小", "configuration.radius");
            return;
        }
        double ratio = radius.sourceCrsValue() / bin.sourceCrsValue();
        if (!Double.isFinite(ratio) || ratio > SpatialDensityConfiguration.MAX_RADIUS_TO_BIN_RATIO) {
            issues.error("SPATIAL_DENSITY_RADIUS_RATIO_EXCEEDED", "搜索半径与格网大小之比不能超过 512", "configuration.radius");
        }
    }

    private static List<CanvasColumnSchema> outputSchema(
            SpatialDensityConfiguration c,
            List<ResolvedField> fields,
            CanvasColumnSchema point
    ) {
        List<CanvasColumnSchema> columns = new ArrayList<>();
        columns.add(new CanvasColumnSchema(c.binIdColumnName(), PlatformDataType.STRING,
                192, null, null, false, null, false, false, "确定性格网标识", null));
        columns.add(new CanvasColumnSchema(c.binGeometryColumnName(), PlatformDataType.GEOMETRY,
                null, null, null, false, null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POLYGON, point.geometry().crs(), CoordinateDimension.XY)));
        if (c.temporalSlicing() != null) {
            columns.add(TrackNodeSupport.timestampColumn(c.temporalSlicing().windowStartColumnName(), false));
            columns.add(TrackNodeSupport.timestampColumn(c.temporalSlicing().windowEndColumnName(), false));
        }
        columns.add(TrackNodeSupport.doubleColumn(c.countDensityColumnName(), false));
        for (ResolvedField field : fields) {
            columns.add(TrackNodeSupport.doubleColumn(field.field().outputColumnName(), false));
        }
        return List.copyOf(columns);
    }

    private static long candidateRange(SpatialDensityBinShape shape, double radius, double side) {
        double spacing = shape == SpatialDensityBinShape.SQUARE ? side : 1.5d * side;
        return (long) Math.ceil(radius / spacing) + 2L;
    }

    private static SpatialBinShape shape(SpatialDensityBinShape shape) {
        return shape == SpatialDensityBinShape.HEXAGON ? SpatialBinShape.HEXAGON : SpatialBinShape.SQUARE;
    }

    private static String valueColumn(int index) {
        return "__datascalpel_density_value_" + index;
    }

    private static String densityColumn(int index) {
        return "__datascalpel_density_result_" + index;
    }

    private static boolean numeric(PlatformDataType type) {
        return switch (type) {
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL -> true;
            default -> false;
        };
    }

    private record ResolvedField(SpatialDensityField field, CanvasColumnSchema source) { }
}

package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialBinShape;
import cn.superhuang.data.scalpel.contract.task.SpatialDensityBinShape;
import cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridStatisticKind;
import cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridVariable;
import cn.superhuang.data.scalpel.contract.task.SpatialMultiVariableGridVariableKind;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.RelationalGroupedDataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.api.java.UDF4;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.UDT.GeometryUDT;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;
import org.apache.spark.sql.types.DataTypes;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** ArcGIS-style multi-variable grid over bounded projected point, line and polygon sources. */
public final class SpatialMultiVariableGridNodeOperator implements CanvasNodeOperator {

    private static final String CELL_ID = "__datascalpel_mvg_cell_id";
    private static final String CELL_GEOMETRY = "__datascalpel_mvg_cell_geometry";
    private static final String CELL_CENTER = "__datascalpel_mvg_cell_center";
    private static final String SOURCE_GEOMETRY = "__datascalpel_mvg_source_geometry";
    private static final String SOURCE_VALUE = "__datascalpel_mvg_source_value";
    private static final String DISTANCE = "__datascalpel_mvg_distance";
    private static final String RANK = "__datascalpel_mvg_rank";
    private static final String Q = "__datascalpel_mvg_q";
    private static final String R = "__datascalpel_mvg_r";

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_MULTI_VARIABLE_GRID;
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
        if (!(definition instanceof SpatialMultiVariableGridNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_MULTI_VARIABLE_GRID operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialMultiVariableGridConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        CanvasNodeIssueSink issues = context.issues();
        validateBase(configuration, inputs, issues);
        Validation validation = validateVariables(configuration, inputs, issues);
        validateOutputNames(configuration, issues);
        if (validation.commonGeometry() == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        SpatialDistanceSupport.Resolution bin = SpatialDistanceSupport.resolve(
                configuration.binSize(), configuration.binSizeUnit(), validation.commonGeometry().crs());
        if (!bin.valid() || bin.angular()) {
            issues.error("SPATIAL_MULTI_VARIABLE_GRID_PROJECTED_CRS_REQUIRED",
                    bin.valid() ? "多变量格网需要投影 CRS" : bin.error(), "configuration.binSizeUnit");
        }
        double side = configuration.binShape() == SpatialDensityBinShape.HEXAGON
                ? bin.sourceCrsValue() / Math.sqrt(3d) : bin.sourceCrsValue();
        if (!Double.isFinite(side) || side <= 0) {
            issues.error("INVALID_SPATIAL_MULTI_VARIABLE_GRID_BIN_SIZE",
                    "格网大小换算结果超出可计算范围", "configuration.binSize");
        }

        List<ResolvedVariable> resolved = resolveDistances(
                validation.variables(), bin, validation.commonGeometry(), issues);
        if (issues.hasErrors() || configuration.binShape() == null || !bin.valid()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> result = buildPlan(configuration, resolved, side,
                validation.commonGeometry().crs().code());
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null,
                outputSchema(configuration, resolved, validation.commonGeometry()),
                CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateBase(
            SpatialMultiVariableGridConfiguration c,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        if (c.variables() == null) {
            issues.error("INVALID_SPATIAL_MULTI_VARIABLE_GRID_VARIABLES", "变量必须是数组", "configuration.variables");
        } else if (c.variables().isEmpty()) {
            issues.error("EMPTY_SPATIAL_MULTI_VARIABLE_GRID_VARIABLES", "至少配置一个格网变量", "configuration.variables");
        } else if (c.variables().size() > SpatialMultiVariableGridConfiguration.MAX_VARIABLES) {
            issues.error("SPATIAL_MULTI_VARIABLE_GRID_VARIABLE_COUNT_EXCEEDED",
                    "格网变量不能超过 32 个", "configuration.variables");
        }
        if (c.binShape() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择格网形状", "configuration.binShape");
        }
        if (!Double.isFinite(c.binSize()) || c.binSize() <= 0 || c.binSizeUnit() == null) {
            issues.error("INVALID_SPATIAL_MULTI_VARIABLE_GRID_BIN_SIZE",
                    "格网大小必须是带单位的有限正数", "configuration.binSize");
        }
        CanvasNodeSupport.required(c.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        CanvasNodeSupport.required(c.binIdColumnName(), "请输入格网 ID 字段名", "configuration.binIdColumnName", issues);
        CanvasNodeSupport.required(c.binGeometryColumnName(), "请输入格网 Geometry 字段名",
                "configuration.binGeometryColumnName", issues);
        if (!CanvasNodeSupport.blank(c.outputTableName()) && inputs.containsKey(c.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + c.outputTableName(), "configuration.outputTableName");
        }
    }

    private static Validation validateVariables(
            SpatialMultiVariableGridConfiguration c,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        if (c.variables() == null) return new Validation(List.of(), null);
        Set<UUID> ids = new HashSet<>();
        List<PartiallyResolvedVariable> result = new ArrayList<>();
        GeometryTypeDefinition common = null;
        for (int index = 0; index < c.variables().size(); index++) {
            SpatialMultiVariableGridVariable variable = c.variables().get(index);
            String path = "configuration.variables[" + index + "]";
            if (variable == null) {
                issues.error("INVALID_SPATIAL_MULTI_VARIABLE_GRID_VARIABLE", "变量配置不能为空", path);
                continue;
            }
            try {
                if (!ids.add(UUID.fromString(variable.variableId()))) {
                    issues.error("DUPLICATE_SPATIAL_MULTI_VARIABLE_GRID_VARIABLE_ID", "变量 ID 重复", path + ".variableId");
                }
            } catch (RuntimeException exception) {
                issues.error("INVALID_SPATIAL_MULTI_VARIABLE_GRID_VARIABLE_ID", "变量 ID 必须是 UUID", path + ".variableId");
            }
            CanvasNodeSupport.required(variable.sourceTableName(), "请选择来源表", path + ".sourceTableName", issues);
            CanvasNodeSupport.required(variable.geometryColumnName(), "请选择 Geometry 字段",
                    path + ".geometryColumnName", issues);
            CanvasNodeSupport.required(variable.outputColumnName(), "请输入变量输出字段名",
                    path + ".outputColumnName", issues);
            if (variable.kind() == null) {
                issues.error("REQUIRED_CONFIGURATION", "请选择变量类型", path + ".kind");
            }
            SparkCanvasTable source = CanvasNodeSupport.blank(variable.sourceTableName())
                    ? null : inputs.get(variable.sourceTableName());
            if (!CanvasNodeSupport.blank(variable.sourceTableName()) && source == null) {
                issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + variable.sourceTableName(),
                        path + ".sourceTableName");
            }
            if (source == null) continue;
            if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
                issues.error("BOUNDED_INPUT_REQUIRED", "多变量格网只支持有界输入", path + ".sourceTableName");
            }
            Map<String, CanvasColumnSchema> columns = CanvasNodeSupport.columns(source.schema());
            CanvasColumnSchema geometry = CanvasNodeSupport.blank(variable.geometryColumnName())
                    ? null : columns.get(variable.geometryColumnName());
            if (!CanvasNodeSupport.blank(variable.geometryColumnName()) && geometry == null) {
                issues.error("COLUMN_NOT_FOUND", "Geometry 字段不存在：" + variable.geometryColumnName(),
                        path + ".geometryColumnName");
            } else if (geometry != null && !supportedGeometry(geometry)) {
                issues.error("SPATIAL_MULTI_VARIABLE_GRID_GEOMETRY_REQUIRED",
                        "格网变量需要带完整元数据的 XY Point、Line 或 Polygon 家族 Geometry",
                        path + ".geometryColumnName");
            }
            if (geometry != null && supportedGeometry(geometry)) {
                if (common == null) common = geometry.geometry();
                else if (!common.crs().equals(geometry.geometry().crs())) {
                    issues.error("SPATIAL_MULTI_VARIABLE_GRID_CRS_MISMATCH",
                            "所有格网变量必须使用同一投影 CRS，请先显式空间转换", path + ".geometryColumnName");
                }
            }
            if (variable.filter() != null) {
                CanvasPredicateExpressionBuilder.validate(variable.filter(), source, issues, path + ".filter");
            }
            CanvasColumnSchema value = validateVariableValue(variable, columns, issues, path);
            validateSearchDistance(variable, issues, path);
            result.add(new PartiallyResolvedVariable(index, variable, source, geometry, value));
        }
        return new Validation(List.copyOf(result), common);
    }

    private static CanvasColumnSchema validateVariableValue(
            SpatialMultiVariableGridVariable variable,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (variable.kind() == SpatialMultiVariableGridVariableKind.ATTRIBUTE_OF_NEAREST) {
            CanvasNodeSupport.required(variable.attributeColumnName(), "请选择最近要素属性字段",
                    path + ".attributeColumnName", issues);
            CanvasColumnSchema value = CanvasNodeSupport.blank(variable.attributeColumnName())
                    ? null : columns.get(variable.attributeColumnName());
            if (!CanvasNodeSupport.blank(variable.attributeColumnName()) && value == null) {
                issues.error("COLUMN_NOT_FOUND", "最近要素属性字段不存在：" + variable.attributeColumnName(),
                        path + ".attributeColumnName");
            } else if (value != null && value.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error("SCALAR_COLUMN_REQUIRED", "最近要素属性必须是非 Geometry 标量字段",
                        path + ".attributeColumnName");
            }
            return value;
        }
        if (variable.kind() != SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED) return null;
        if (variable.statisticKind() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择关联要素统计类型", path + ".statisticKind");
            return null;
        }
        if (variable.statisticKind() == SpatialMultiVariableGridStatisticKind.COUNT) return null;
        CanvasNodeSupport.required(variable.statisticColumnName(), "请选择统计字段",
                path + ".statisticColumnName", issues);
        CanvasColumnSchema value = CanvasNodeSupport.blank(variable.statisticColumnName())
                ? null : columns.get(variable.statisticColumnName());
        if (!CanvasNodeSupport.blank(variable.statisticColumnName()) && value == null) {
            issues.error("COLUMN_NOT_FOUND", "统计字段不存在：" + variable.statisticColumnName(),
                    path + ".statisticColumnName");
        } else if (value != null && variable.statisticKind() == SpatialMultiVariableGridStatisticKind.ANY
                && value.fieldType() != PlatformDataType.STRING) {
            issues.error("STRING_COLUMN_REQUIRED", "ANY 统计要求字符串字段", path + ".statisticColumnName");
        } else if (value != null && variable.statisticKind() != SpatialMultiVariableGridStatisticKind.ANY
                && !numeric(value.fieldType())) {
            issues.error("NUMERIC_COLUMN_REQUIRED", "该统计需要数值字段", path + ".statisticColumnName");
        }
        return value;
    }

    private static void validateSearchDistance(
            SpatialMultiVariableGridVariable variable,
            CanvasNodeIssueSink issues,
            String path
    ) {
        boolean required = variable.kind() == SpatialMultiVariableGridVariableKind.DISTANCE_TO_NEAREST
                || variable.kind() == SpatialMultiVariableGridVariableKind.ATTRIBUTE_OF_NEAREST;
        if (required && variable.searchDistance() == null) {
            issues.error("SPATIAL_MULTI_VARIABLE_GRID_SEARCH_DISTANCE_REQUIRED",
                    "最近距离和最近属性必须配置搜索距离", path + ".searchDistance");
            return;
        }
        if (variable.searchDistance() != null && (!Double.isFinite(variable.searchDistance())
                || variable.searchDistance() <= 0 || variable.searchDistanceUnit() == null)) {
            issues.error("INVALID_SPATIAL_MULTI_VARIABLE_GRID_SEARCH_DISTANCE",
                    "搜索距离必须是带单位的有限正数", path + ".searchDistance");
        }
    }

    private static List<ResolvedVariable> resolveDistances(
            List<PartiallyResolvedVariable> variables,
            SpatialDistanceSupport.Resolution bin,
            GeometryTypeDefinition common,
            CanvasNodeIssueSink issues
    ) {
        List<ResolvedVariable> result = new ArrayList<>();
        for (PartiallyResolvedVariable partial : variables) {
            SpatialMultiVariableGridVariable variable = partial.variable();
            double search = Double.NaN;
            double units = Double.NaN;
            if (variable.searchDistance() != null && variable.searchDistanceUnit() != null) {
                SpatialDistanceSupport.Resolution resolved = SpatialDistanceSupport.resolve(
                        variable.searchDistance(), variable.searchDistanceUnit(), common.crs());
                SpatialDistanceSupport.Resolution unit = SpatialDistanceSupport.sourceUnitsPerConfiguredUnit(
                        variable.searchDistanceUnit(), common.crs());
                if (!resolved.valid() || resolved.angular() || !unit.valid() || unit.angular()) {
                    issues.error("SPATIAL_MULTI_VARIABLE_GRID_PROJECTED_CRS_REQUIRED",
                            resolved.valid() ? "多变量格网搜索距离需要投影 CRS" : resolved.error(),
                            "configuration.variables[" + partial.index() + "].searchDistanceUnit");
                } else {
                    search = resolved.sourceCrsValue();
                    units = unit.sourceCrsValue();
                    if (bin.valid() && !bin.angular() && bin.sourceCrsValue() > 0) {
                        double ratio = search / bin.sourceCrsValue();
                        if (!Double.isFinite(ratio)
                                || ratio > SpatialMultiVariableGridConfiguration.MAX_SEARCH_TO_BIN_RATIO) {
                            issues.error("SPATIAL_MULTI_VARIABLE_GRID_SEARCH_RATIO_EXCEEDED",
                                    "搜索距离与格网大小之比不能超过 512",
                                    "configuration.variables[" + partial.index() + "].searchDistance");
                        }
                    }
                }
            }
            result.add(new ResolvedVariable(partial.index(), variable, partial.source(), partial.geometry(),
                    partial.value(), search, units));
        }
        return List.copyOf(result);
    }

    private static void validateOutputNames(
            SpatialMultiVariableGridConfiguration c,
            CanvasNodeIssueSink issues
    ) {
        Set<String> names = new HashSet<>();
        addName(c.binIdColumnName(), "configuration.binIdColumnName", names, issues);
        addName(c.binGeometryColumnName(), "configuration.binGeometryColumnName", names, issues);
        if (c.variables() != null) for (int index = 0; index < c.variables().size(); index++) {
            SpatialMultiVariableGridVariable variable = c.variables().get(index);
            if (variable != null) addName(variable.outputColumnName(),
                    "configuration.variables[" + index + "].outputColumnName", names, issues);
        }
    }

    private static void addName(String name, String path, Set<String> names, CanvasNodeIssueSink issues) {
        if (!CanvasNodeSupport.blank(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
            issues.error("DUPLICATE_COLUMN_NAME", "输出字段名重复：" + name, path);
        }
    }

    private static Dataset<Row> buildPlan(
            SpatialMultiVariableGridConfiguration c,
            List<ResolvedVariable> variables,
            double side,
            int srid
    ) {
        Dataset<Row> extentRows = null;
        Set<String> extentSources = new LinkedHashSet<>();
        for (ResolvedVariable variable : variables) {
            String key = variable.variable().sourceTableName() + "\u0000" + variable.variable().geometryColumnName();
            if (!extentSources.add(key)) continue;
            Dataset<Row> source = validGeometryRows(variable.source().dataset(),
                    variable.variable().geometryColumnName()).select(
                    functions.col(SOURCE_GEOMETRY).alias(SOURCE_GEOMETRY));
            extentRows = extentRows == null ? source : extentRows.unionByName(source);
        }
        if (extentRows == null) throw new IllegalStateException("Validated multi-variable grid has no extent source");
        SpatialBinShape shape = c.binShape() == SpatialDensityBinShape.HEXAGON
                ? SpatialBinShape.HEXAGON : SpatialBinShape.SQUARE;
        Dataset<Row> grid = buildGrid(extentRows, shape, side, srid);
        Dataset<Row> result = grid;
        for (ResolvedVariable variable : variables) {
            Dataset<Row> value = buildVariable(grid, variable);
            result = result.join(value, CELL_ID, "left");
            if (variable.variable().kind() == SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED
                    && variable.variable().statisticKind() == SpatialMultiVariableGridStatisticKind.COUNT) {
                result = result.withColumn(variable.variable().outputColumnName(), functions.coalesce(
                        result.col(CanvasNodeSupport.quoteIdentifier(variable.variable().outputColumnName())), functions.lit(0L)));
            }
        }
        List<Column> projection = new ArrayList<>();
        projection.add(result.col(CELL_ID).alias(c.binIdColumnName()));
        projection.add(result.col(CELL_GEOMETRY).alias(c.binGeometryColumnName()));
        for (ResolvedVariable variable : variables) {
            projection.add(result.col(CanvasNodeSupport.quoteIdentifier(variable.variable().outputColumnName())));
        }
        return result.select(projection.toArray(Column[]::new));
    }

    private static Dataset<Row> buildGrid(
            Dataset<Row> extentRows,
            SpatialBinShape shape,
            double side,
            int srid
    ) {
        Dataset<Row> bounds = extentRows.agg(
                functions.min(st_functions.ST_XMin(extentRows.col(SOURCE_GEOMETRY))).alias("x0"),
                functions.max(st_functions.ST_XMax(extentRows.col(SOURCE_GEOMETRY))).alias("x1"),
                functions.min(st_functions.ST_YMin(extentRows.col(SOURCE_GEOMETRY))).alias("y0"),
                functions.max(st_functions.ST_YMax(extentRows.col(SOURCE_GEOMETRY))).alias("y1"));
        var rangeType = DataTypes.createStructType(List.of(
                DataTypes.createStructField("q0", DataTypes.LongType, false),
                DataTypes.createStructField("q1", DataTypes.LongType, false),
                DataTypes.createStructField("r0", DataTypes.LongType, false),
                DataTypes.createStructField("r1", DataTypes.LongType, false)));
        Column scope = functions.udf((UDF4<Double, Double, Double, Double, Geometry>) (x0, x1, y0, y1) ->
                        x0 == null || x1 == null || y0 == null || y1 == null ? null
                                : new GeometryFactory().toGeometry(new Envelope(x0, x1, y0, y1)),
                new GeometryUDT()).apply(bounds.col("x0"), bounds.col("x1"), bounds.col("y0"), bounds.col("y1"));
        Column range = functions.udf((UDF4<Double, Double, Double, Double, Row>) (minX, maxX, minY, maxY) -> {
            if (minX == null || maxX == null || minY == null || maxY == null) return null;
            double q0;
            double q1;
            double r0;
            double r1;
            if (shape == SpatialBinShape.SQUARE) {
                q0 = Math.floor(minX / side);
                q1 = Math.floor(maxX / side);
                r0 = Math.floor(minY / side);
                r1 = Math.floor(maxY / side);
            } else {
                q0 = Math.floor((minX / side - 1d) / 1.5d);
                q1 = Math.ceil((maxX / side + 1d) / 1.5d);
                r0 = Math.floor(minY / side / Math.sqrt(3d) - 0.5d - q1 / 2d);
                r1 = Math.ceil(maxY / side / Math.sqrt(3d) + 0.5d - q0 / 2d);
            }
            for (double value : new double[]{q0, q1, r0, r1}) {
                if (!Double.isFinite(value) || Math.abs(value) > (1L << 50)) {
                    throw new IllegalArgumentException("SPATIAL_MULTI_VARIABLE_GRID_GEOMETRY_INVALID");
                }
            }
            if ((q1 - q0 + 1d) * (r1 - r0 + 1d) > SpatialMultiVariableGridConfiguration.MAX_OUTPUT_CELLS) {
                throw new IllegalArgumentException("SPATIAL_MULTI_VARIABLE_GRID_CELL_LIMIT_EXCEEDED");
            }
            return RowFactory.create((long) q0, (long) q1, (long) r0, (long) r1);
        }, rangeType).apply(bounds.col("x0"), bounds.col("x1"), bounds.col("y0"), bounds.col("y1"));
        Dataset<Row> cells = bounds.select(range.alias("range"), scope.alias("scope"))
                .filter("range is not null")
                .withColumn(Q, functions.explode(functions.sequence(
                        functions.col("range.q0"), functions.col("range.q1"))))
                .withColumn(R, functions.explode(functions.sequence(
                        functions.col("range.r0"), functions.col("range.r1"))));
        Column cellGeometry = PlanarGridSupport.geometry(cells.col(Q), cells.col(R), shape, side, srid, null);
        cells = cells.select(cells.col("scope"), cells.col(Q), cells.col(R),
                functions.concat_ws(":", functions.lit(PlanarGridSupport.identity(null, shape, side, srid)),
                        cells.col(Q), cells.col(R)).alias(CELL_ID),
                cellGeometry.alias(CELL_GEOMETRY));
        Column inScope = st_functions.ST_Area(cells.col("scope")).gt(0)
                .and(st_functions.ST_Area(st_functions.ST_Intersection(
                        cells.col(CELL_GEOMETRY), cells.col("scope"))).gt(0))
                .or(st_functions.ST_Area(cells.col("scope")).equalTo(0)
                        .and(st_predicates.ST_Intersects(cells.col(CELL_GEOMETRY), cells.col("scope"))));
        return cells.filter(inScope).select(cells.col(CELL_ID), cells.col(CELL_GEOMETRY),
                st_functions.ST_Centroid(cells.col(CELL_GEOMETRY)).alias(CELL_CENTER));
    }

    private static Dataset<Row> buildVariable(Dataset<Row> grid, ResolvedVariable resolved) {
        SpatialMultiVariableGridVariable variable = resolved.variable();
        Dataset<Row> source = resolved.source().dataset();
        if (variable.filter() != null) {
            source = source.filter(CanvasPredicateExpressionBuilder.expression(variable.filter(), source));
        }
        source = validGeometryRows(source, variable.geometryColumnName());
        List<Column> sourceProjection = new ArrayList<>(List.of(source.col(SOURCE_GEOMETRY)));
        if (resolved.value() != null) {
            String valueName = variable.kind() == SpatialMultiVariableGridVariableKind.ATTRIBUTE_OF_NEAREST
                    ? variable.attributeColumnName() : variable.statisticColumnName();
            sourceProjection.add(source.col(CanvasNodeSupport.quoteIdentifier(valueName)).alias(SOURCE_VALUE));
        }
        Dataset<Row> features = source.select(sourceProjection.toArray(Column[]::new)).alias("s");
        Dataset<Row> cells = grid.alias("g");
        boolean usesDistance = variable.searchDistance() != null;
        Column distance = st_functions.ST_Distance(qualified("g", CELL_CENTER), qualified("s", SOURCE_GEOMETRY));
        Column relation = usesDistance
                ? distance.leq(resolved.searchDistanceInCrs())
                : st_predicates.ST_Intersects(qualified("g", CELL_GEOMETRY), qualified("s", SOURCE_GEOMETRY));
        Dataset<Row> matches = cells.join(features, relation, "inner");
        if (variable.kind() == SpatialMultiVariableGridVariableKind.DISTANCE_TO_NEAREST
                || variable.kind() == SpatialMultiVariableGridVariableKind.ATTRIBUTE_OF_NEAREST) {
            WindowSpec nearest = Window.partitionBy(qualified("g", CELL_ID)).orderBy(
                    distance.asc(),
                    functions.sha2(st_functions.ST_AsBinary(qualified("s", SOURCE_GEOMETRY)), 256).asc(),
                    resolved.value() == null ? functions.lit("").asc()
                            : qualified("s", SOURCE_VALUE).cast("string").asc_nulls_last());
            Dataset<Row> ranked = matches.withColumn(DISTANCE, distance).withColumn(RANK,
                    functions.row_number().over(nearest)).filter(functions.col(RANK).equalTo(1));
            Column value = variable.kind() == SpatialMultiVariableGridVariableKind.DISTANCE_TO_NEAREST
                    ? ranked.col(DISTANCE).divide(resolved.sourceUnitsPerOutputUnit())
                    : qualified("s", SOURCE_VALUE);
            return ranked.select(qualified("g", CELL_ID).alias(CELL_ID),
                    value.alias(variable.outputColumnName()));
        }

        Column sourceValue = resolved.value() == null ? null : qualified("s", SOURCE_VALUE);
        RelationalGroupedDataset grouped = matches.groupBy(qualified("g", CELL_ID));
        Column aggregate = switch (variable.statisticKind()) {
            case COUNT -> functions.count(qualified("s", SOURCE_GEOMETRY));
            case ANY -> functions.min(sourceValue);
            case SUM -> functions.sum(checkedFinite(sourceValue));
            case MEAN -> functions.avg(checkedFinite(sourceValue));
            case MIN -> functions.min(checkedFinite(sourceValue));
            case MAX -> functions.max(checkedFinite(sourceValue));
            case RANGE -> functions.max(checkedFinite(sourceValue)).minus(functions.min(checkedFinite(sourceValue)));
            case STDDEV -> functions.stddev_samp(checkedFinite(sourceValue));
            case VARIANCE -> functions.var_samp(checkedFinite(sourceValue));
        };
        return grouped.agg(aggregate.alias(variable.outputColumnName()))
                .withColumnRenamed(CELL_ID, CELL_ID);
    }

    private static Dataset<Row> validGeometryRows(Dataset<Row> source, String geometryColumnName) {
        Column geometry = source.col(CanvasNodeSupport.quoteIdentifier(geometryColumnName));
        Column valid = functions.udf((UDF1<Geometry, Boolean>) value -> {
            if (value == null || value.isEmpty()) return false;
            if (!value.isValid()) throw new IllegalArgumentException("SPATIAL_MULTI_VARIABLE_GRID_GEOMETRY_INVALID");
            for (var coordinate : value.getCoordinates()) {
                if (!Double.isFinite(coordinate.x) || !Double.isFinite(coordinate.y)) {
                    throw new IllegalArgumentException("SPATIAL_MULTI_VARIABLE_GRID_GEOMETRY_INVALID");
                }
            }
            return true;
        }, DataTypes.BooleanType).apply(geometry);
        return source.filter(valid).withColumn(SOURCE_GEOMETRY, geometry);
    }

    private static Column checkedFinite(Column value) {
        Column numeric = value.cast("double");
        return functions.when(value.isNull(), functions.lit(null).cast("double"))
                .when(functions.isnan(numeric).or(functions.abs(numeric).gt(Double.MAX_VALUE)),
                        functions.raise_error(functions.lit(
                                "SPATIAL_MULTI_VARIABLE_GRID_VALUE_NOT_FINITE")).cast("double"))
                .otherwise(numeric);
    }

    private static Column qualified(String alias, String columnName) {
        return functions.col(alias + "." + CanvasNodeSupport.quoteIdentifier(columnName));
    }

    private static List<CanvasColumnSchema> outputSchema(
            SpatialMultiVariableGridConfiguration c,
            List<ResolvedVariable> variables,
            GeometryTypeDefinition common
    ) {
        List<CanvasColumnSchema> columns = new ArrayList<>();
        columns.add(new CanvasColumnSchema(c.binIdColumnName(), PlatformDataType.STRING,
                192, null, null, false, null, false, false, "确定性格网标识", null));
        columns.add(new CanvasColumnSchema(c.binGeometryColumnName(), PlatformDataType.GEOMETRY,
                null, null, null, false, null, false, false, null,
                new GeometryTypeDefinition(GeometryKind.POLYGON, common.crs(), CoordinateDimension.XY)));
        for (ResolvedVariable resolved : variables) {
            SpatialMultiVariableGridVariable variable = resolved.variable();
            if (variable.kind() == SpatialMultiVariableGridVariableKind.ATTRIBUTE_OF_NEAREST
                    || variable.kind() == SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED
                    && variable.statisticKind() == SpatialMultiVariableGridStatisticKind.ANY) {
                CanvasColumnSchema source = resolved.value();
                columns.add(new CanvasColumnSchema(variable.outputColumnName(), source.fieldType(),
                        source.length(), source.precision(), source.scale(), true,
                        null, false, false, null, null));
            } else if (variable.kind() == SpatialMultiVariableGridVariableKind.ATTRIBUTE_SUMMARY_OF_RELATED
                    && variable.statisticKind() == SpatialMultiVariableGridStatisticKind.COUNT) {
                columns.add(new CanvasColumnSchema(variable.outputColumnName(), PlatformDataType.LONG,
                        null, null, null, false, null, false, false, null, null));
            } else {
                columns.add(TrackNodeSupport.doubleColumn(variable.outputColumnName(), true));
            }
        }
        return List.copyOf(columns);
    }

    private static boolean supportedGeometry(CanvasColumnSchema column) {
        if (column.fieldType() != PlatformDataType.GEOMETRY || column.geometry() == null
                || column.geometry().dimension() != CoordinateDimension.XY) return false;
        return switch (column.geometry().kind()) {
            case POINT, MULTIPOINT, LINESTRING, MULTILINESTRING, POLYGON, MULTIPOLYGON -> true;
            default -> false;
        };
    }

    private static boolean numeric(PlatformDataType type) {
        return switch (type) {
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL -> true;
            default -> false;
        };
    }

    private record PartiallyResolvedVariable(
            int index,
            SpatialMultiVariableGridVariable variable,
            SparkCanvasTable source,
            CanvasColumnSchema geometry,
            CanvasColumnSchema value
    ) { }

    private record ResolvedVariable(
            int index,
            SpatialMultiVariableGridVariable variable,
            SparkCanvasTable source,
            CanvasColumnSchema geometry,
            CanvasColumnSchema value,
            double searchDistanceInCrs,
            double sourceUnitsPerOutputUnit
    ) { }

    private record Validation(List<PartiallyResolvedVariable> variables, GeometryTypeDefinition commonGeometry) { }
}

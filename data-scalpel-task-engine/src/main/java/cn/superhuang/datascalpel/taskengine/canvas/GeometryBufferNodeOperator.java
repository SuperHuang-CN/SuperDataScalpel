package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferDistanceSource;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureMode;
import cn.superhuang.data.scalpel.contract.task.TrackSplitExpressionPolicy;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.catalyst.expressions.Expression;
import org.apache.spark.sql.catalyst.expressions.Generator;
import org.apache.spark.sql.catalyst.expressions.SubqueryExpression;
import org.apache.spark.sql.catalyst.expressions.WindowExpression;
import org.apache.spark.sql.catalyst.expressions.aggregate.AggregateExpression;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.NumericType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GeometryBufferNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_BUFFER;
    }

    @Override
    public CanvasNodeCategory category() {
        return CanvasNodeCategory.PROCESSOR;
    }

    @Override
    public Set<CanvasExecutionMode> supportedModes() {
        return Set.of(CanvasExecutionMode.BATCH, CanvasExecutionMode.STREAMING);
    }

    @Override
    public CanvasNodeOperationResult apply(
            CanvasNodeDefinition definition,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeOperationContext context
    ) {
        if (!(definition instanceof GeometryBufferNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "GEOMETRY_BUFFER operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        GeometryBufferConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        CanvasNodeSupport.required(configuration.geometryColumnName(), "请选择 Geometry 字段",
                "configuration.geometryColumnName", issues);
        CanvasNodeSupport.required(configuration.outputColumnName(), "请输入 Buffer 结果字段名",
                "configuration.outputColumnName", issues);
        GeometryBufferDistanceSource distanceSource = configuration.effectiveDistanceSource();
        if (distanceSource == GeometryBufferDistanceSource.CONSTANT
                && (!Double.isFinite(configuration.distance()) || configuration.distance() <= 0)) {
            issues.error("INVALID_GEOMETRY_BUFFER_DISTANCE",
                    "Buffer 距离必须是有限正数", "configuration.distance");
        }
        if (distanceSource == GeometryBufferDistanceSource.FIELD) {
            CanvasNodeSupport.required(configuration.distanceFieldName(), "请选择 Buffer 距离字段",
                    "configuration.distanceFieldName", issues);
        }
        if (distanceSource == GeometryBufferDistanceSource.EXPRESSION) {
            CanvasNodeSupport.required(configuration.distanceExpression(), "请输入 Buffer 距离表达式",
                    "configuration.distanceExpression", issues);
            if (!CanvasNodeSupport.blank(configuration.distanceExpression())
                    && TrackSplitExpressionPolicy.findViolation(configuration.distanceExpression()) != null) {
                issues.error("INVALID_GEOMETRY_BUFFER_EXPRESSION",
                        "请输入单个受控逐行数值表达式，不允许 SQL 语句或自定义窗口",
                        "configuration.distanceExpression");
            }
        }
        if (configuration.mode() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择 Buffer 模式", "configuration.mode");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }

        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }
        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        CanvasColumnSchema geometryColumn = CanvasNodeSupport.blank(configuration.geometryColumnName())
                ? null : sourceColumns.get(configuration.geometryColumnName());
        if (source != null && !CanvasNodeSupport.blank(configuration.geometryColumnName())
                && geometryColumn == null) {
            issues.error("COLUMN_NOT_FOUND",
                    "Geometry 字段不存在：" + configuration.geometryColumnName(),
                    "configuration.geometryColumnName");
        } else if (geometryColumn != null
                && geometryColumn.fieldType() != PlatformDataType.GEOMETRY) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                    "所选字段不是 Geometry：" + configuration.geometryColumnName(),
                    "configuration.geometryColumnName");
        }
        double distanceFactor = Double.NaN;
        if (geometryColumn != null
                && geometryColumn.fieldType() == PlatformDataType.GEOMETRY) {
            CanvasNodeSupport.validateSupportedGeometry(
                    List.of(geometryColumn), "configuration.geometryColumnName", issues);
            GeometryTypeDefinition geometry = geometryColumn.geometry();
            if (geometry != null && configuration.mode() == SpatialMeasureMode.SPHEROID
                    && !isWgs84(geometry)) {
                issues.error("SPHEROID_BUFFER_REQUIRES_WGS84",
                        "椭球 Buffer 仅支持 EPSG:4326",
                        "configuration.geometryColumnName");
            }
            if (geometry != null && configuration.mode() != null) {
                distanceFactor = resolveDistanceFactor(configuration, geometry, issues);
            }
        }
        Set<String> names = new HashSet<>(sourceColumns.keySet());
        if (!CanvasNodeSupport.blank(configuration.outputColumnName())
                && !names.add(configuration.outputColumnName())) {
            issues.error("DUPLICATE_COLUMN_NAME",
                    "输出字段名重复：" + configuration.outputColumnName(),
                    "configuration.outputColumnName");
        }
        Column configuredDistance = source == null ? null : configuredDistance(
                source, configuration, distanceSource, issues);
        if (source == null || issues.hasErrors() || configuredDistance == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        GeometryTypeDefinition sourceGeometry = geometryColumn.geometry();
        Dataset<Row> sourceDataset = source.dataset();
        Column geometry = sourceDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.geometryColumnName()));
        Column convertedDistance = configuredDistance.cast(DataTypes.DoubleType)
                .multiply(distanceFactor);
        Column validDistance = functions.when(
                configuredDistance.isNull(),
                functions.lit(null).cast(DataTypes.DoubleType)
        ).when(
                convertedDistance.isNotNull()
                        .and(functions.not(functions.isnan(convertedDistance)))
                        .and(convertedDistance.gt(0d))
                        .and(convertedDistance.leq(Double.MAX_VALUE)),
                convertedDistance
        ).otherwise(functions.raise_error(
                functions.lit("GEOMETRY_BUFFER_DISTANCE_VALUE_INVALID"))
                .cast(DataTypes.DoubleType));
        Column buffered = configuration.mode() == SpatialMeasureMode.SPHEROID
                ? st_functions.ST_Buffer(
                        geometry, validDistance, functions.lit(true))
                : st_functions.ST_Buffer(geometry, validDistance);
        Column normalized = st_functions.ST_SetSRID(
                st_functions.ST_Multi(buffered),
                functions.lit(sourceGeometry.crs().code())
        ).alias(configuration.outputColumnName());
        List<Column> projection = new ArrayList<>(source.schema().columns().size() + 1);
        for (CanvasColumnSchema column : source.schema().columns()) {
            projection.add(sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name())));
        }
        projection.add(normalized);
        Dataset<Row> bufferedDataset = sourceDataset.select(projection.toArray(Column[]::new));

        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        outputColumns.add(new CanvasColumnSchema(
                configuration.outputColumnName(), PlatformDataType.GEOMETRY,
                null, null, null, geometryColumn.nullable(),
                null, false, false, null,
                new GeometryTypeDefinition(
                        GeometryKind.MULTIPOLYGON,
                        sourceGeometry.crs(),
                        sourceGeometry.dimension()
                )
        ));
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                source.schema().datasetKind(), source.schema().eventTimeColumn(),
                source.schema().watermarkDelay());
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, bufferedDataset));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static boolean isWgs84(GeometryTypeDefinition geometry) {
        return "EPSG".equals(geometry.crs().authority())
                && geometry.crs().code() == 4326;
    }

    private static Column configuredDistance(
            SparkCanvasTable source,
            GeometryBufferConfiguration configuration,
            GeometryBufferDistanceSource distanceSource,
            CanvasNodeIssueSink issues
    ) {
        Column distance;
        String path;
        if (distanceSource == GeometryBufferDistanceSource.CONSTANT) {
            return functions.lit(configuration.distance());
        }
        if (distanceSource == GeometryBufferDistanceSource.FIELD) {
            path = "configuration.distanceFieldName";
            if (CanvasNodeSupport.blank(configuration.distanceFieldName())) return null;
            if (!CanvasNodeSupport.columns(source.schema()).containsKey(configuration.distanceFieldName())) {
                issues.error("COLUMN_NOT_FOUND",
                        "Buffer 距离字段不存在：" + configuration.distanceFieldName(), path);
                return null;
            }
            distance = source.dataset().col(
                    CanvasNodeSupport.quoteIdentifier(configuration.distanceFieldName()));
        } else {
            path = "configuration.distanceExpression";
            if (CanvasNodeSupport.blank(configuration.distanceExpression())
                    || TrackSplitExpressionPolicy.findViolation(configuration.distanceExpression()) != null) {
                return null;
            }
            distance = functions.expr(configuration.distanceExpression());
        }
        try {
            String alias = "__datascalpel_buffer_distance";
            Dataset<Row> analyzed = source.dataset().select(distance.alias(alias));
            if (!(analyzed.schema().apply(0).dataType() instanceof NumericType)) {
                throw new IllegalArgumentException("Buffer distance is not numeric");
            }
            var expressions = analyzed.queryExecution().analyzed().expressions().iterator();
            while (expressions.hasNext()) {
                if (!scalar(expressions.next())) throw new IllegalArgumentException("Buffer distance is not scalar");
            }
            if (!scalarPlan(analyzed.queryExecution().analyzed(),
                    source.dataset().queryExecution().analyzed())) {
                throw new IllegalArgumentException("Buffer distance changes row membership");
            }
            source.dataset().withColumn(alias, distance).schema();
        } catch (Exception exception) {
            issues.error(
                    distanceSource == GeometryBufferDistanceSource.FIELD
                            ? "INVALID_GEOMETRY_BUFFER_DISTANCE_SOURCE"
                            : "INVALID_GEOMETRY_BUFFER_EXPRESSION",
                    distanceSource == GeometryBufferDistanceSource.FIELD
                            ? "Buffer 距离字段必须是数值类型"
                            : "Buffer 距离必须是确定性的逐行数值表达式，不允许聚合、展开、窗口或子查询",
                    path
            );
            return null;
        }
        return distance;
    }

    private static boolean scalar(Expression expression) {
        if (!expression.deterministic()
                || expression instanceof AggregateExpression
                || expression instanceof Generator
                || expression instanceof SubqueryExpression
                || expression instanceof WindowExpression) {
            return false;
        }
        var children = expression.children().iterator();
        while (children.hasNext()) {
            if (!scalar(children.next())) return false;
        }
        return true;
    }

    private static boolean scalarPlan(
            org.apache.spark.sql.catalyst.plans.logical.LogicalPlan plan,
            org.apache.spark.sql.catalyst.plans.logical.LogicalPlan input
    ) {
        if (plan.equals(input)) return true;
        var expressions = plan.expressions().iterator();
        while (expressions.hasNext()) {
            if (!scalar(expressions.next())) return false;
        }
        var children = plan.children().iterator();
        while (children.hasNext()) {
            if (!scalarPlan(children.next(), input)) return false;
        }
        return true;
    }

    private static double resolveDistanceFactor(
            GeometryBufferConfiguration configuration,
            GeometryTypeDefinition geometry,
            CanvasNodeIssueSink issues
    ) {
        SpatialDistanceUnit unit = configuration.effectiveDistanceUnit();
        if (configuration.mode() == SpatialMeasureMode.SPHEROID) {
            double factor = SpatialDistanceSupport.metresPerConfiguredUnit(unit);
            if (!Double.isFinite(factor)) {
                issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED",
                        "椭球 Buffer 必须使用明确的线性距离单位，不能使用来源 CRS 单位",
                        "configuration.distanceUnit");
            }
            return factor;
        }
        SpatialDistanceSupport.Resolution resolution = SpatialDistanceSupport.resolve(
                1d, unit, geometry.crs());
        if (!resolution.valid()) {
            issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED", resolution.error(),
                    "configuration.distanceUnit");
            return Double.NaN;
        }
        if (resolution.angular()) {
            issues.warning("PLANAR_BUFFER_USES_ANGULAR_UNITS",
                    "地理 CRS 的平面 Buffer 使用来源角度单位",
                    "configuration.distanceUnit");
        }
        return resolution.sourceCrsValue();
    }
}

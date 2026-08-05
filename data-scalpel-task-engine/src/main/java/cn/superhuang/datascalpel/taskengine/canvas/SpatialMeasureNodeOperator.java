package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureMode;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasurement;
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
import java.util.Map;
import java.util.Set;

public final class SpatialMeasureNodeOperator implements CanvasNodeOperator {
    private static final Set<GeometryKind> POLYGON_KINDS = Set.of(
            GeometryKind.POLYGON,
            GeometryKind.MULTIPOLYGON
    );
    private static final Set<GeometryKind> LINE_KINDS = Set.of(
            GeometryKind.LINESTRING,
            GeometryKind.MULTILINESTRING
    );

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_MEASURE;
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
        if (!(definition instanceof SpatialMeasureNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "SPATIAL_MEASURE operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialMeasureConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }
        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }

        if (configuration.measurements() == null) {
            issues.error("INVALID_SPATIAL_MEASUREMENT", "空间测量项必须是数组",
                    "configuration.measurements");
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (configuration.measurements().isEmpty()) {
            issues.error("EMPTY_SPATIAL_MEASUREMENTS", "至少配置一个空间测量项",
                    "configuration.measurements");
        }
        if (configuration.measurements().size() > SpatialMeasureConfiguration.MAX_MEASUREMENTS) {
            issues.error(
                    "SPATIAL_MEASUREMENT_LIMIT_EXCEEDED",
                    "空间测量项不能超过 " + SpatialMeasureConfiguration.MAX_MEASUREMENTS + " 项",
                    "configuration.measurements"
            );
        }

        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        Set<String> outputNames = new HashSet<>(sourceColumns.keySet());
        for (int index = 0; index < configuration.measurements().size(); index++) {
            SpatialMeasurement measurement = configuration.measurements().get(index);
            String path = "configuration.measurements[" + index + "]";
            if (measurement == null) {
                issues.error("INVALID_SPATIAL_MEASUREMENT", "空间测量项不能为空", path);
                continue;
            }
            CanvasNodeSupport.required(
                    measurement.outputColumnName(), "请输入测量输出字段名",
                    path + ".outputColumnName", issues);
            if (!CanvasNodeSupport.blank(measurement.outputColumnName())
                    && !outputNames.add(measurement.outputColumnName())) {
                issues.error(
                        "DUPLICATE_COLUMN_NAME",
                        "测量输出字段名重复：" + measurement.outputColumnName(),
                        path + ".outputColumnName"
                );
            }
            validateMeasurement(measurement, sourceColumns, source != null, issues, path);
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        List<Column> projection = new ArrayList<>(
                source.schema().columns().size() + configuration.measurements().size());
        for (CanvasColumnSchema column : source.schema().columns()) {
            projection.add(sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name())));
        }
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        for (SpatialMeasurement measurement : configuration.measurements()) {
            projection.add(measurementExpression(measurement, sourceDataset)
                    .alias(measurement.outputColumnName()));
            outputColumns.add(new CanvasColumnSchema(
                    measurement.outputColumnName(), PlatformDataType.DOUBLE,
                    null, null, null, true, null, false, false, null
            ));
        }
        Dataset<Row> measured = sourceDataset.select(projection.toArray(Column[]::new));
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                source.schema().datasetKind(), source.schema().eventTimeColumn(),
                source.schema().watermarkDelay());
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, measured));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateMeasurement(
            SpatialMeasurement measurement,
            Map<String, CanvasColumnSchema> columns,
            boolean tableAvailable,
            CanvasNodeIssueSink issues,
            String path
    ) {
        switch (measurement) {
            case SpatialMeasurement.Area item -> validateUnaryMeasurement(
                    item.geometryColumnName(), item.mode(), POLYGON_KINDS,
                    columns, tableAvailable, issues, path);
            case SpatialMeasurement.Length item -> validateUnaryMeasurement(
                    item.geometryColumnName(), item.mode(), LINE_KINDS,
                    columns, tableAvailable, issues, path);
            case SpatialMeasurement.Perimeter item -> validateUnaryMeasurement(
                    item.geometryColumnName(), item.mode(), POLYGON_KINDS,
                    columns, tableAvailable, issues, path);
            case SpatialMeasurement.Distance item -> validateDistance(
                    item, columns, tableAvailable, issues, path);
            case SpatialMeasurement.X item -> validateCoordinate(
                    item.geometryColumnName(), columns, tableAvailable, issues, path);
            case SpatialMeasurement.Y item -> validateCoordinate(
                    item.geometryColumnName(), columns, tableAvailable, issues, path);
        }
    }

    private static void validateUnaryMeasurement(
            String columnName,
            SpatialMeasureMode mode,
            Set<GeometryKind> acceptedKinds,
            Map<String, CanvasColumnSchema> columns,
            boolean tableAvailable,
            CanvasNodeIssueSink issues,
            String path
    ) {
        CanvasColumnSchema column = validateGeometryColumn(
                columnName, columns, tableAvailable, issues,
                path + ".geometryColumnName");
        validateMode(mode, path + ".mode", issues);
        if (column == null || column.fieldType() != PlatformDataType.GEOMETRY
                || column.geometry() == null) return;
        if (!acceptedKinds.contains(column.geometry().kind())) {
            issues.error(
                    "SPATIAL_MEASURE_KIND_UNSUPPORTED",
                    "GeometryKind 不支持当前测量：" + column.geometry().kind(),
                    path + ".geometryColumnName"
            );
        }
        if (supportedGeometryDefinition(column.geometry())) {
            validateModeCrs(
                    mode,
                    column.geometry(),
                    issues,
                    path + ".geometryColumnName",
                    path + ".mode"
            );
        }
    }

    private static void validateDistance(
            SpatialMeasurement.Distance item,
            Map<String, CanvasColumnSchema> columns,
            boolean tableAvailable,
            CanvasNodeIssueSink issues,
            String path
    ) {
        CanvasColumnSchema left = validateGeometryColumn(
                item.leftGeometryColumnName(), columns, tableAvailable, issues,
                path + ".leftGeometryColumnName");
        CanvasColumnSchema right = validateGeometryColumn(
                item.rightGeometryColumnName(), columns, tableAvailable, issues,
                path + ".rightGeometryColumnName");
        validateMode(item.mode(), path + ".mode", issues);
        if (!validGeometry(left) || !validGeometry(right)) return;
        GeometryTypeDefinition leftGeometry = left.geometry();
        GeometryTypeDefinition rightGeometry = right.geometry();
        if (!supportedGeometryDefinition(leftGeometry)
                || !supportedGeometryDefinition(rightGeometry)) return;
        boolean compatible = true;
        if (leftGeometry.dimension() != rightGeometry.dimension()) {
            issues.error(
                    "SPATIAL_MEASURE_CRS_MISMATCH",
                    "距离测量两侧坐标维度不一致",
                    path
            );
            compatible = false;
        }
        if (item.mode() == SpatialMeasureMode.PLANAR
                && !leftGeometry.crs().equals(rightGeometry.crs())) {
            issues.error(
                    "SPATIAL_MEASURE_CRS_MISMATCH",
                    "平面距离测量两侧 CRS 必须完全一致",
                    path
            );
            compatible = false;
        }
        if (!compatible) return;
        validateModeCrs(
                item.mode(),
                leftGeometry,
                issues,
                path + ".leftGeometryColumnName",
                path + ".mode"
        );
        if (item.mode() == SpatialMeasureMode.SPHEROID
                && !isWgs84(rightGeometry)) {
            issues.error(
                    "SPHEROID_MEASURE_REQUIRES_WGS84",
                    "椭球距离测量两侧都必须是 EPSG:4326",
                    path + ".rightGeometryColumnName"
            );
        }
    }

    private static void validateCoordinate(
            String columnName,
            Map<String, CanvasColumnSchema> columns,
            boolean tableAvailable,
            CanvasNodeIssueSink issues,
            String path
    ) {
        CanvasColumnSchema column = validateGeometryColumn(
                columnName, columns, tableAvailable, issues,
                path + ".geometryColumnName");
        if (validGeometry(column) && column.geometry().kind() != GeometryKind.POINT) {
            issues.error(
                    "SPATIAL_MEASURE_KIND_UNSUPPORTED",
                    "X/Y 只支持 POINT Geometry",
                    path + ".geometryColumnName"
            );
        }
    }

    private static CanvasColumnSchema validateGeometryColumn(
            String columnName,
            Map<String, CanvasColumnSchema> columns,
            boolean tableAvailable,
            CanvasNodeIssueSink issues,
            String path
    ) {
        CanvasNodeSupport.required(columnName, "请选择 Geometry 字段", path, issues);
        if (!tableAvailable || CanvasNodeSupport.blank(columnName)) return null;
        CanvasColumnSchema column = columns.get(columnName);
        if (column == null) {
            issues.error("COLUMN_NOT_FOUND", "Geometry 字段不存在：" + columnName, path);
            return null;
        }
        if (column.fieldType() != PlatformDataType.GEOMETRY) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                    "所选字段不是 Geometry：" + columnName, path);
            return column;
        }
        CanvasNodeSupport.validateSupportedGeometry(List.of(column), path, issues);
        return column;
    }

    private static void validateMode(
            SpatialMeasureMode mode,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (mode == null) {
            issues.error("INVALID_SPATIAL_MEASUREMENT", "请选择空间测量模式", path);
        }
    }

    private static void validateModeCrs(
            SpatialMeasureMode mode,
            GeometryTypeDefinition geometry,
            CanvasNodeIssueSink issues,
            String geometryPath,
            String modePath
    ) {
        if (mode == SpatialMeasureMode.SPHEROID && !isWgs84(geometry)) {
            issues.error(
                    "SPHEROID_MEASURE_REQUIRES_WGS84",
                    "椭球测量仅支持 EPSG:4326",
                    geometryPath
            );
        } else if (mode == SpatialMeasureMode.PLANAR && isWgs84(geometry)) {
            issues.warning(
                    "PLANAR_MEASURE_USES_ANGULAR_UNITS",
                    "EPSG:4326 的平面测量使用角度或角度平方作为单位",
                    modePath
            );
        }
    }

    private static boolean supportedGeometryDefinition(GeometryTypeDefinition geometry) {
        return geometry != null
                && "EPSG".equals(geometry.crs().authority())
                && geometry.dimension() == CoordinateDimension.XY;
    }

    private static boolean validGeometry(CanvasColumnSchema column) {
        return column != null
                && column.fieldType() == PlatformDataType.GEOMETRY
                && column.geometry() != null;
    }

    private static boolean isWgs84(GeometryTypeDefinition geometry) {
        return geometry != null
                && "EPSG".equals(geometry.crs().authority())
                && geometry.crs().code() == 4326
                && geometry.dimension() == CoordinateDimension.XY;
    }

    private static Column measurementExpression(
            SpatialMeasurement measurement,
            Dataset<Row> dataset
    ) {
        return switch (measurement) {
            case SpatialMeasurement.Area item -> item.mode() == SpatialMeasureMode.SPHEROID
                    ? st_functions.ST_AreaSpheroid(column(dataset, item.geometryColumnName()))
                    : st_functions.ST_Area(column(dataset, item.geometryColumnName()));
            case SpatialMeasurement.Length item -> item.mode() == SpatialMeasureMode.SPHEROID
                    ? st_functions.ST_LengthSpheroid(column(dataset, item.geometryColumnName()))
                    : st_functions.ST_Length(column(dataset, item.geometryColumnName()));
            case SpatialMeasurement.Perimeter item -> item.mode() == SpatialMeasureMode.SPHEROID
                    ? st_functions.ST_Perimeter(
                            column(dataset, item.geometryColumnName()), functions.lit(true))
                    : st_functions.ST_Perimeter(column(dataset, item.geometryColumnName()));
            case SpatialMeasurement.Distance item -> item.mode() == SpatialMeasureMode.SPHEROID
                    ? st_functions.ST_DistanceSpheroid(
                            column(dataset, item.leftGeometryColumnName()),
                            column(dataset, item.rightGeometryColumnName()))
                    : st_functions.ST_Distance(
                            column(dataset, item.leftGeometryColumnName()),
                            column(dataset, item.rightGeometryColumnName()));
            case SpatialMeasurement.X item ->
                    st_functions.ST_X(column(dataset, item.geometryColumnName()));
            case SpatialMeasurement.Y item ->
                    st_functions.ST_Y(column(dataset, item.geometryColumnName()));
        };
    }

    private static Column column(Dataset<Row> dataset, String name) {
        return dataset.col(CanvasNodeSupport.quoteIdentifier(name));
    }
}

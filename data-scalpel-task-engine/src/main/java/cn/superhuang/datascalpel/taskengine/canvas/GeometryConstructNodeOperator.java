package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.GeometryConstructSource;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_constructors;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GeometryConstructNodeOperator implements CanvasNodeOperator {
    private static final Set<PlatformDataType> NUMERIC_TYPES = Set.of(
            PlatformDataType.BYTE,
            PlatformDataType.SHORT,
            PlatformDataType.INTEGER,
            PlatformDataType.LONG,
            PlatformDataType.FLOAT,
            PlatformDataType.DOUBLE,
            PlatformDataType.DECIMAL
    );

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_CONSTRUCT;
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
        if (!(definition instanceof GeometryConstructNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "GEOMETRY_CONSTRUCT operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        GeometryConstructConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(
                configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(
                configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        CanvasNodeSupport.required(
                configuration.outputColumnName(), "请输入 Geometry 输出字段名",
                "configuration.outputColumnName", issues);
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error(
                    "DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName"
            );
        }

        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        if (!CanvasNodeSupport.blank(configuration.outputColumnName())
                && sourceColumns.containsKey(configuration.outputColumnName())) {
            issues.error(
                    "DUPLICATE_COLUMN_NAME",
                    "Geometry 输出字段已存在：" + configuration.outputColumnName(),
                    "configuration.outputColumnName"
            );
        }

        GeometryTypeDefinition targetGeometry = configuration.targetGeometry();
        validateTargetGeometry(targetGeometry, configuration.source(), issues);
        validateSource(configuration.source(), sourceColumns, source != null, issues);
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        Column geometry = geometryExpression(configuration.source(), sourceDataset);
        geometry = st_functions.ST_SetSRID(geometry, functions.lit(targetGeometry.crs().code()));
        if (!(configuration.source() instanceof GeometryConstructSource.PointFromXy)) {
            Column sourceIsNull = sourceIsNull(configuration.source(), sourceDataset);
            Column expectedKind = st_functions.GeometryType(geometry)
                    .equalTo(functions.lit(targetGeometry.kind().name()));
            geometry = functions.when(sourceIsNull, geometry)
                    .when(expectedKind, geometry)
                    .otherwise(functions.raise_error(
                            functions.lit("GEOMETRY_CONSTRUCT_KIND_MISMATCH")
                    ));
        }

        boolean nullable = sourceNullable(configuration.source(), sourceColumns);
        CanvasColumnSchema geometryColumn = new CanvasColumnSchema(
                configuration.outputColumnName(),
                PlatformDataType.GEOMETRY,
                null,
                null,
                null,
                nullable,
                null,
                false,
                false,
                null,
                targetGeometry
        );
        List<Column> projection = new ArrayList<>(source.schema().columns().size() + 1);
        for (CanvasColumnSchema column : source.schema().columns()) {
            projection.add(sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name())));
        }
        projection.add(geometry.as(
                configuration.outputColumnName(),
                SparkTypeMapper.metadata(geometryColumn)
        ));
        Dataset<Row> constructed = sourceDataset.select(projection.toArray(Column[]::new));
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        outputColumns.add(geometryColumn);
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(),
                null,
                outputColumns,
                source.schema().datasetKind(),
                source.schema().eventTimeColumn(),
                source.schema().watermarkDelay()
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, constructed));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateTargetGeometry(
            GeometryTypeDefinition target,
            GeometryConstructSource source,
            CanvasNodeIssueSink issues
    ) {
        if (target == null) {
            issues.error(
                    "GEOMETRY_CONSTRUCT_KIND_UNSUPPORTED",
                    "请选择目标 Geometry 类型和 CRS",
                    "configuration.targetGeometry"
            );
            return;
        }
        if (target.kind() == GeometryKind.GEOMETRY) {
            issues.error(
                    "GEOMETRY_CONSTRUCT_KIND_UNSUPPORTED",
                    "Geometry 构造必须声明具体 GeometryKind",
                    "configuration.targetGeometry.kind"
            );
        }
        if (target.dimension() != CoordinateDimension.XY) {
            issues.error(
                    "GEOMETRY_CONSTRUCT_KIND_UNSUPPORTED",
                    "Geometry 构造第一阶段只支持 XY 维度",
                    "configuration.targetGeometry.dimension"
            );
        }
        if (!"EPSG".equals(target.crs().authority()) || target.crs().code() < 1) {
            issues.error(
                    "UNSUPPORTED_GEOMETRY_CRS",
                    "Geometry 构造第一阶段只支持 EPSG CRS",
                    "configuration.targetGeometry.crs"
            );
        }
        if (source instanceof GeometryConstructSource.PointFromXy
                && target.kind() != GeometryKind.POINT) {
            issues.error(
                    "GEOMETRY_CONSTRUCT_KIND_UNSUPPORTED",
                    "POINT_FROM_XY 的目标 GeometryKind 必须是 POINT",
                    "configuration.targetGeometry.kind"
            );
        }
    }

    private static void validateSource(
            GeometryConstructSource source,
            Map<String, CanvasColumnSchema> columns,
            boolean tableAvailable,
            CanvasNodeIssueSink issues
    ) {
        if (source == null) {
            issues.error(
                    "INVALID_GEOMETRY_CONSTRUCT_SOURCE",
                    "请选择 Geometry 构造来源",
                    "configuration.source"
            );
            return;
        }
        switch (source) {
            case GeometryConstructSource.Wkt item -> validateColumn(
                    item.columnName(), PlatformDataType.STRING, columns, tableAvailable,
                    "configuration.source.columnName", issues);
            case GeometryConstructSource.Wkb item -> validateColumn(
                    item.columnName(), PlatformDataType.BINARY, columns, tableAvailable,
                    "configuration.source.columnName", issues);
            case GeometryConstructSource.GeoJson item -> validateColumn(
                    item.columnName(), PlatformDataType.STRING, columns, tableAvailable,
                    "configuration.source.columnName", issues);
            case GeometryConstructSource.PointFromXy item -> {
                validateNumericColumn(
                        item.xColumnName(), columns, tableAvailable,
                        "configuration.source.xColumnName", issues);
                validateNumericColumn(
                        item.yColumnName(), columns, tableAvailable,
                        "configuration.source.yColumnName", issues);
            }
        }
    }

    private static void validateColumn(
            String name,
            PlatformDataType expectedType,
            Map<String, CanvasColumnSchema> columns,
            boolean tableAvailable,
            String path,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(name, "请选择来源字段", path, issues);
        if (!tableAvailable || CanvasNodeSupport.blank(name)) return;
        CanvasColumnSchema column = columns.get(name);
        if (column == null) {
            issues.error("COLUMN_NOT_FOUND", "来源字段不存在：" + name, path);
        } else if (column.fieldType() != expectedType) {
            issues.error(
                    "GEOMETRY_CONSTRUCT_SOURCE_TYPE_MISMATCH",
                    "来源字段必须是 " + expectedType + "：" + name,
                    path
            );
        }
    }

    private static void validateNumericColumn(
            String name,
            Map<String, CanvasColumnSchema> columns,
            boolean tableAvailable,
            String path,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(name, "请选择坐标字段", path, issues);
        if (!tableAvailable || CanvasNodeSupport.blank(name)) return;
        CanvasColumnSchema column = columns.get(name);
        if (column == null) {
            issues.error("COLUMN_NOT_FOUND", "坐标字段不存在：" + name, path);
        } else if (!NUMERIC_TYPES.contains(column.fieldType())) {
            issues.error(
                    "GEOMETRY_CONSTRUCT_SOURCE_TYPE_MISMATCH",
                    "坐标字段必须是数值类型：" + name,
                    path
            );
        }
    }

    private static Column geometryExpression(
            GeometryConstructSource source,
            Dataset<Row> dataset
    ) {
        return switch (source) {
            case GeometryConstructSource.Wkt item -> st_constructors.ST_GeomFromWKT(
                    dataset.col(CanvasNodeSupport.quoteIdentifier(item.columnName())));
            case GeometryConstructSource.Wkb item -> st_constructors.ST_GeomFromWKB(
                    dataset.col(CanvasNodeSupport.quoteIdentifier(item.columnName())));
            case GeometryConstructSource.GeoJson item -> st_constructors.ST_GeomFromGeoJSON(
                    dataset.col(CanvasNodeSupport.quoteIdentifier(item.columnName())));
            case GeometryConstructSource.PointFromXy item -> st_constructors.ST_Point(
                    dataset.col(CanvasNodeSupport.quoteIdentifier(item.xColumnName())).cast("double"),
                    dataset.col(CanvasNodeSupport.quoteIdentifier(item.yColumnName())).cast("double")
            );
        };
    }

    private static Column sourceIsNull(GeometryConstructSource source, Dataset<Row> dataset) {
        return switch (source) {
            case GeometryConstructSource.Wkt item -> dataset
                    .col(CanvasNodeSupport.quoteIdentifier(item.columnName())).isNull();
            case GeometryConstructSource.Wkb item -> dataset
                    .col(CanvasNodeSupport.quoteIdentifier(item.columnName())).isNull();
            case GeometryConstructSource.GeoJson item -> dataset
                    .col(CanvasNodeSupport.quoteIdentifier(item.columnName())).isNull();
            case GeometryConstructSource.PointFromXy item -> dataset
                    .col(CanvasNodeSupport.quoteIdentifier(item.xColumnName())).isNull()
                    .or(dataset.col(CanvasNodeSupport.quoteIdentifier(item.yColumnName())).isNull());
        };
    }

    private static boolean sourceNullable(
            GeometryConstructSource source,
            Map<String, CanvasColumnSchema> columns
    ) {
        return switch (source) {
            case GeometryConstructSource.Wkt item -> columns.get(item.columnName()).nullable();
            case GeometryConstructSource.Wkb item -> columns.get(item.columnName()).nullable();
            case GeometryConstructSource.GeoJson item -> columns.get(item.columnName()).nullable();
            case GeometryConstructSource.PointFromXy item ->
                    columns.get(item.xColumnName()).nullable()
                            || columns.get(item.yColumnName()).nullable();
        };
    }
}

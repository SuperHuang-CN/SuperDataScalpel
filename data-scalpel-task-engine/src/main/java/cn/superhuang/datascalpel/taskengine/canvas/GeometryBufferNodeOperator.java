package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryBufferNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialMeasureMode;
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
        if (!Double.isFinite(configuration.distance()) || configuration.distance() <= 0) {
            issues.error("INVALID_GEOMETRY_BUFFER_DISTANCE",
                    "Buffer 距离必须是有限正数", "configuration.distance");
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
        } else if (geometryColumn != null) {
            CanvasNodeSupport.validateSupportedGeometry(
                    List.of(geometryColumn), "configuration.geometryColumnName", issues);
            GeometryTypeDefinition geometry = geometryColumn.geometry();
            if (geometry != null && configuration.mode() == SpatialMeasureMode.SPHEROID
                    && !isWgs84(geometry)) {
                issues.error("SPHEROID_BUFFER_REQUIRES_WGS84",
                        "椭球 Buffer 仅支持 EPSG:4326",
                        "configuration.geometryColumnName");
            } else if (geometry != null && configuration.mode() == SpatialMeasureMode.PLANAR
                    && isWgs84(geometry)) {
                issues.warning("PLANAR_BUFFER_USES_ANGULAR_UNITS",
                        "EPSG:4326 的平面 Buffer 使用角度作为距离单位",
                        "configuration.mode");
            }
        }
        Set<String> names = new HashSet<>(sourceColumns.keySet());
        if (!CanvasNodeSupport.blank(configuration.outputColumnName())
                && !names.add(configuration.outputColumnName())) {
            issues.error("DUPLICATE_COLUMN_NAME",
                    "输出字段名重复：" + configuration.outputColumnName(),
                    "configuration.outputColumnName");
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        GeometryTypeDefinition sourceGeometry = geometryColumn.geometry();
        Dataset<Row> sourceDataset = source.dataset();
        Column geometry = sourceDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.geometryColumnName()));
        Column buffered = configuration.mode() == SpatialMeasureMode.SPHEROID
                ? st_functions.ST_Buffer(
                        geometry, functions.lit(configuration.distance()), functions.lit(true))
                : st_functions.ST_Buffer(geometry, functions.lit(configuration.distance()));
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
}

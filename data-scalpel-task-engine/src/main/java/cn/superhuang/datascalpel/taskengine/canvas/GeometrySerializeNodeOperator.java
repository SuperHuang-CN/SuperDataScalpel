package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.GeometrySerializationFormat;
import cn.superhuang.data.scalpel.contract.task.GeometrySerializeConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometrySerializeNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GeometrySerializeNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_SERIALIZE;
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
        if (!(definition instanceof GeometrySerializeNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "GEOMETRY_SERIALIZE operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        GeometrySerializeConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        CanvasNodeSupport.required(configuration.geometryColumnName(), "请选择 Geometry 字段",
                "configuration.geometryColumnName", issues);
        CanvasNodeSupport.required(configuration.outputColumnName(), "请输入序列化输出字段名",
                "configuration.outputColumnName", issues);
        if (configuration.format() == null) {
            issues.error("INVALID_GEOMETRY_SERIALIZATION_FORMAT", "请选择序列化格式",
                    "configuration.format");
        }
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
        Map<String, CanvasColumnSchema> sourceColumns = source == null
                ? Map.of() : CanvasNodeSupport.columns(source.schema());
        CanvasColumnSchema geometryColumn = CanvasNodeSupport.blank(configuration.geometryColumnName())
                ? null : sourceColumns.get(configuration.geometryColumnName());
        if (source != null && !CanvasNodeSupport.blank(configuration.geometryColumnName())
                && geometryColumn == null) {
            issues.error("COLUMN_NOT_FOUND", "Geometry 字段不存在：" + configuration.geometryColumnName(),
                    "configuration.geometryColumnName");
        } else if (geometryColumn != null && geometryColumn.fieldType() != PlatformDataType.GEOMETRY) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "所选字段不是 Geometry："
                    + configuration.geometryColumnName(), "configuration.geometryColumnName");
        } else if (geometryColumn != null) {
            CanvasNodeSupport.validateSupportedGeometry(
                    List.of(geometryColumn), "configuration.geometryColumnName", issues);
            if (configuration.format() == GeometrySerializationFormat.GEOJSON
                    && geometryColumn.geometry() != null
                    && (geometryColumn.geometry().crs().code() != 4326
                    || !"EPSG".equals(geometryColumn.geometry().crs().authority()))) {
                issues.error(
                        "GEOJSON_REQUIRES_WGS84",
                        "GeoJSON 序列化仅支持 EPSG:4326，请先使用空间转换节点",
                        "configuration.geometryColumnName"
                );
            }
        }
        if (!CanvasNodeSupport.blank(configuration.outputColumnName())
                && sourceColumns.containsKey(configuration.outputColumnName())) {
            issues.error("DUPLICATE_COLUMN_NAME", "输出字段已存在：" + configuration.outputColumnName(),
                    "configuration.outputColumnName");
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        Column geometry = sourceDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.geometryColumnName()));
        Column serialized = switch (configuration.format()) {
            case WKT -> st_functions.ST_AsText(geometry);
            case WKB -> st_functions.ST_AsBinary(geometry);
            case GEOJSON -> st_functions.ST_AsGeoJSON(geometry);
        };
        List<Column> projection = new ArrayList<>(source.schema().columns().size() + 1);
        for (CanvasColumnSchema column : source.schema().columns()) {
            projection.add(sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name())));
        }
        projection.add(serialized.alias(configuration.outputColumnName()));
        Dataset<Row> serializedDataset = sourceDataset.select(projection.toArray(Column[]::new));
        PlatformDataType outputType = configuration.format() == GeometrySerializationFormat.WKB
                ? PlatformDataType.BINARY : PlatformDataType.STRING;
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        outputColumns.add(new CanvasColumnSchema(
                configuration.outputColumnName(), outputType, null, null, null,
                geometryColumn.nullable(), null, false, false, null
        ));
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                source.schema().datasetKind(), source.schema().eventTimeColumn(),
                source.schema().watermarkDelay());
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, serializedDataset));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }
}

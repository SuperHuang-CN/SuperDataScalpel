package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialTransformConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialTransformNodeDefinition;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.CrsReference;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SpatialTransformNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_TRANSFORM;
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
        if (!(definition instanceof SpatialTransformNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_TRANSFORM operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialTransformConfiguration configuration = node.configuration();
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
                configuration.geometryColumnName(), "请选择空间字段",
                "configuration.geometryColumnName", issues);
        if (configuration.targetCrs() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择目标 CRS", "configuration.targetCrs");
        } else {
            validateCrs(configuration.targetCrs(), "configuration.targetCrs", issues);
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error(
                    "DUPLICATE_TABLE_NAME",
                    "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName"
            );
        }
        SparkCanvasTable source = inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error(
                    "TABLE_NOT_FOUND",
                    "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName"
            );
        }
        if (source == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        CanvasColumnSchema geometryColumn =
                CanvasNodeSupport.columns(source.schema()).get(configuration.geometryColumnName());
        if (!CanvasNodeSupport.blank(configuration.geometryColumnName()) && geometryColumn == null) {
            issues.error(
                    "COLUMN_NOT_FOUND",
                    "空间字段不存在：" + configuration.geometryColumnName(),
                    "configuration.geometryColumnName"
            );
        } else if (geometryColumn != null && geometryColumn.fieldType() != PlatformDataType.GEOMETRY) {
            issues.error(
                    "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                    "所选字段不是 Geometry：" + configuration.geometryColumnName(),
                    "configuration.geometryColumnName"
            );
        } else if (geometryColumn != null) {
            validateGeometry(geometryColumn.geometry(), "configuration.geometryColumnName", issues);
        }
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        GeometryTypeDefinition sourceGeometry = geometryColumn.geometry();
        CrsReference targetCrs = configuration.targetCrs();
        boolean unchanged = sourceGeometry.crs().equals(targetCrs);
        if (unchanged) {
            issues.warning(
                    "SPATIAL_TRANSFORM_HAS_NO_EFFECT",
                    "来源 CRS 与目标 CRS 相同，不会改变空间坐标",
                    "configuration.targetCrs"
            );
        }
        List<CanvasColumnSchema> targetColumns = source.schema().columns().stream()
                .map(column -> column.name().equals(configuration.geometryColumnName())
                        ? withCrs(column, targetCrs)
                        : column)
                .toList();
        Dataset<Row> sourceDataset = source.dataset();
        Column[] projection = new Column[targetColumns.size()];
        for (int index = 0; index < targetColumns.size(); index++) {
            CanvasColumnSchema column = source.schema().columns().get(index);
            Column sourceColumn = sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name()));
            projection[index] = column.name().equals(configuration.geometryColumnName()) && !unchanged
                    ? st_functions.ST_Transform(
                            sourceColumn,
                            functions.lit(crsName(sourceGeometry.crs())),
                            functions.lit(crsName(targetCrs))
                    ).alias(column.name())
                    : sourceColumn.alias(column.name());
        }
        Dataset<Row> transformed = sourceDataset.select(projection);
        CanvasTableSchema schema = new CanvasTableSchema(
                configuration.outputTableName(),
                null,
                SparkTypeMapper.fromStructType(transformed.schema(), targetColumns),
                source.schema().datasetKind(),
                source.schema().eventTimeColumn(),
                source.schema().watermarkDelay()
        );
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(schema.name(), new SparkCanvasTable(schema, transformed));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static CanvasColumnSchema withCrs(CanvasColumnSchema column, CrsReference targetCrs) {
        GeometryTypeDefinition source = column.geometry();
        return new CanvasColumnSchema(
                column.name(), column.fieldType(), null, null, null, column.nullable(),
                column.defaultValue(), column.autoIncrement(), column.generated(), column.comment(),
                new GeometryTypeDefinition(source.kind(), targetCrs, source.dimension())
        );
    }

    private static String crsName(CrsReference crs) {
        return crs.authority() + ":" + crs.code();
    }

    private static void validateGeometry(
            GeometryTypeDefinition geometry,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (geometry == null) {
            issues.error("GEOMETRY_TYPE_DEFINITION_REQUIRED", "空间字段缺少 Geometry 定义", path);
            return;
        }
        validateCrs(geometry.crs(), path, issues);
        if (geometry.dimension() != CoordinateDimension.XY) {
            issues.error("UNSUPPORTED_GEOMETRY_DIMENSION", "空间计算第一阶段只支持 XY 维度", path);
        }
    }

    static void validateCrs(CrsReference crs, String path, CanvasNodeIssueSink issues) {
        if (crs == null || !"EPSG".equals(crs.authority()) || crs.code() < 1) {
            issues.error("UNSUPPORTED_GEOMETRY_CRS", "空间计算第一阶段只支持 EPSG CRS", path);
        }
    }
}

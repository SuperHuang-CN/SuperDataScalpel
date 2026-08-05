package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.GeometryValidateConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryValidateNodeDefinition;
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

public final class GeometryValidateNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_VALIDATE;
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
        if (!(definition instanceof GeometryValidateNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "GEOMETRY_VALIDATE operator received " + definition.nodeType()
            );
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        GeometryValidateConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        CanvasNodeIssueSink issues = context.issues();
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        CanvasNodeSupport.required(configuration.geometryColumnName(), "请选择 Geometry 字段",
                "configuration.geometryColumnName", issues);
        CanvasNodeSupport.required(configuration.validColumnName(), "请输入合法性结果字段名",
                "configuration.validColumnName", issues);
        if (configuration.reasonColumnName() != null) {
            CanvasNodeSupport.required(configuration.reasonColumnName(), "请输入原因字段名",
                    "configuration.reasonColumnName", issues);
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
        }

        Set<String> outputNames = new HashSet<>(sourceColumns.keySet());
        validateOutputName(configuration.validColumnName(), "configuration.validColumnName",
                outputNames, issues);
        if (configuration.reasonColumnName() != null) {
            validateOutputName(configuration.reasonColumnName(), "configuration.reasonColumnName",
                    outputNames, issues);
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        Column geometry = sourceDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.geometryColumnName()));
        Column valid = st_functions.ST_IsValid(geometry);
        List<Column> projection = new ArrayList<>(source.schema().columns().size() + 2);
        for (CanvasColumnSchema column : source.schema().columns()) {
            projection.add(sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name())));
        }
        projection.add(valid.alias(configuration.validColumnName()));
        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        outputColumns.add(scalarColumn(
                configuration.validColumnName(),
                PlatformDataType.BOOLEAN,
                geometryColumn.nullable()
        ));
        if (configuration.reasonColumnName() != null) {
            Column reason = functions.when(
                    valid.equalTo(functions.lit(false)),
                    st_functions.ST_IsValidReason(geometry)
            ).otherwise(functions.lit(null).cast("string"));
            projection.add(reason.alias(configuration.reasonColumnName()));
            outputColumns.add(scalarColumn(
                    configuration.reasonColumnName(),
                    PlatformDataType.STRING,
                    true
            ));
        }
        Dataset<Row> validated = sourceDataset.select(projection.toArray(Column[]::new));
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                source.schema().datasetKind(), source.schema().eventTimeColumn(),
                source.schema().watermarkDelay());
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, validated));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateOutputName(
            String name,
            String path,
            Set<String> names,
            CanvasNodeIssueSink issues
    ) {
        if (!CanvasNodeSupport.blank(name) && !names.add(name)) {
            issues.error("DUPLICATE_COLUMN_NAME", "输出字段名重复：" + name, path);
        }
    }

    private static CanvasColumnSchema scalarColumn(
            String name,
            PlatformDataType type,
            boolean nullable
    ) {
        return new CanvasColumnSchema(
                name, type, null, null, null, nullable,
                null, false, false, null
        );
    }
}

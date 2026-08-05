package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.GeometryExplodeConfiguration;
import cn.superhuang.data.scalpel.contract.task.GeometryExplodeNodeDefinition;
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

public final class GeometryExplodeNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.GEOMETRY_EXPLODE;
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
        if (!(definition instanceof GeometryExplodeNodeDefinition node)) {
            throw new IllegalArgumentException(
                    "GEOMETRY_EXPLODE operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        GeometryExplodeConfiguration configuration = node.configuration();
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
        CanvasNodeSupport.required(configuration.outputColumnName(), "请输入部件字段名",
                "configuration.outputColumnName", issues);
        if (configuration.partIndexColumnName() != null) {
            CanvasNodeSupport.required(configuration.partIndexColumnName(), "请输入部件序号字段名",
                    "configuration.partIndexColumnName", issues);
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
        }
        Set<String> names = new HashSet<>(sourceColumns.keySet());
        validateOutputName(
                configuration.outputColumnName(),
                "configuration.outputColumnName",
                names,
                issues
        );
        if (configuration.partIndexColumnName() != null) {
            validateOutputName(
                    configuration.partIndexColumnName(),
                    "configuration.partIndexColumnName",
                    names,
                    issues
            );
        }
        if (source == null || issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        GeometryTypeDefinition sourceGeometry = geometryColumn.geometry();
        Dataset<Row> sourceDataset = source.dataset();
        Column geometry = sourceDataset.col(
                CanvasNodeSupport.quoteIdentifier(configuration.geometryColumnName()));
        Column components = st_functions.ST_Dump(geometry);
        Set<String> occupiedNames = new HashSet<>(names);
        String componentTemporaryName = temporaryName(occupiedNames, "__canvas_geometry_part");
        String indexTemporaryName = configuration.partIndexColumnName() == null
                ? null : temporaryName(occupiedNames, "__canvas_geometry_part_index");

        List<Column> generatedProjection = new ArrayList<>(source.schema().columns().size() + 1);
        for (CanvasColumnSchema column : source.schema().columns()) {
            generatedProjection.add(sourceDataset.col(CanvasNodeSupport.quoteIdentifier(column.name())));
        }
        if (indexTemporaryName == null) {
            generatedProjection.add(
                    functions.explode_outer(components).alias(componentTemporaryName));
        } else {
            generatedProjection.add(
                    functions.posexplode_outer(components)
                            .as(new String[]{indexTemporaryName, componentTemporaryName}));
        }
        Dataset<Row> generated = sourceDataset.select(generatedProjection.toArray(Column[]::new));

        List<Column> finalProjection = new ArrayList<>(source.schema().columns().size() + 2);
        for (CanvasColumnSchema column : source.schema().columns()) {
            finalProjection.add(generated.col(CanvasNodeSupport.quoteIdentifier(column.name())));
        }
        finalProjection.add(st_functions.ST_SetSRID(
                generated.col(CanvasNodeSupport.quoteIdentifier(componentTemporaryName)),
                functions.lit(sourceGeometry.crs().code())
        ).alias(configuration.outputColumnName()));
        if (indexTemporaryName != null) {
            finalProjection.add(
                    generated.col(CanvasNodeSupport.quoteIdentifier(indexTemporaryName))
                            .alias(configuration.partIndexColumnName()));
        }
        Dataset<Row> explodedDataset = generated.select(finalProjection.toArray(Column[]::new));

        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        outputColumns.add(new CanvasColumnSchema(
                configuration.outputColumnName(), PlatformDataType.GEOMETRY,
                null, null, null, true,
                null, false, false, null,
                new GeometryTypeDefinition(
                        componentKind(sourceGeometry.kind()),
                        sourceGeometry.crs(),
                        sourceGeometry.dimension()
                )
        ));
        if (configuration.partIndexColumnName() != null) {
            outputColumns.add(new CanvasColumnSchema(
                    configuration.partIndexColumnName(), PlatformDataType.INTEGER,
                    null, null, null, true,
                    null, false, false, null
            ));
        }
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                source.schema().datasetKind(), source.schema().eventTimeColumn(),
                source.schema().watermarkDelay());
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, explodedDataset));
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

    private static String temporaryName(Set<String> occupied, String base) {
        String name = base;
        int suffix = 1;
        while (!occupied.add(name)) {
            name = base + "_" + suffix++;
        }
        return name;
    }

    private static GeometryKind componentKind(GeometryKind sourceKind) {
        return switch (sourceKind) {
            case MULTIPOINT -> GeometryKind.POINT;
            case MULTILINESTRING -> GeometryKind.LINESTRING;
            case MULTIPOLYGON -> GeometryKind.POLYGON;
            case POINT, LINESTRING, POLYGON -> sourceKind;
            case GEOMETRY, GEOMETRYCOLLECTION -> GeometryKind.GEOMETRY;
        };
    }
}

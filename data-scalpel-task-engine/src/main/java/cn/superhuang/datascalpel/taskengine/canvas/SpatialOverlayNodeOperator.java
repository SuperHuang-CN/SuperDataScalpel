package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.JoinOutputColumnSource;
import cn.superhuang.data.scalpel.contract.task.SpatialOverlayConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialOverlayNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialOverlayOperation;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.compiler.lineage.CatalystLineageMetadata;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_aggregates;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;
import org.apache.spark.sql.sedona_sql.expressions.st_predicates;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class SpatialOverlayNodeOperator implements CanvasNodeOperator {

    private static final String LEFT_ALIAS = "overlay_left";
    private static final String RIGHT_ALIAS = "overlay_right";
    private static final String LEFT_MASK_ALIAS = "overlay_left_mask";
    private static final String RIGHT_MASK_ALIAS = "overlay_right_mask";

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_OVERLAY;
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
        if (!(definition instanceof SpatialOverlayNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_OVERLAY operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialOverlayConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        CanvasNodeIssueSink issues = context.issues();
        requireConfiguration(configuration, inputs, issues);
        SparkCanvasTable left = CanvasNodeSupport.blank(configuration.leftTableName()) ? null : inputs.get(configuration.leftTableName());
        SparkCanvasTable right = CanvasNodeSupport.blank(configuration.rightTableName()) ? null : inputs.get(configuration.rightTableName());
        validateTable(left, configuration.leftTableName(), "左侧", "configuration.leftTableName", issues);
        validateTable(right, configuration.rightTableName(), "右侧", "configuration.rightTableName", issues);
        if (left == null || right == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Map<String, CanvasColumnSchema> leftColumns = CanvasNodeSupport.columns(left.schema());
        Map<String, CanvasColumnSchema> rightColumns = CanvasNodeSupport.columns(right.schema());
        CanvasColumnSchema leftGeometry = validateGeometry(
                configuration.leftGeometryColumnName(), leftColumns, "左侧",
                "configuration.leftGeometryColumnName", issues);
        CanvasColumnSchema rightGeometry = validateGeometry(
                configuration.rightGeometryColumnName(), rightColumns, "右侧",
                "configuration.rightGeometryColumnName", issues);
        validateGeometryPair(leftGeometry, rightGeometry, issues);
        if (leftGeometry != null && rightGeometry != null) {
            SpatialOverlayGeometrySupport.validate(configuration, leftGeometry.geometry(), rightGeometry.geometry(), issues);
        }
        List<JoinOutputColumnSupport.ResolvedOutputColumn> outputs = JoinOutputColumnSupport.validate(
                configuration.outputColumns(), leftColumns, rightColumns, issues);
        validateOutputColumns(configuration, outputs, issues);
        if (issues.hasErrors() || leftGeometry == null || rightGeometry == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> leftDataset = SpatialOverlayGeometrySupport.prepare(left.dataset(),
                configuration.leftGeometryColumnName(), configuration.usesFamilyGeometry()).alias(LEFT_ALIAS);
        Dataset<Row> rightDataset = SpatialOverlayGeometrySupport.prepare(right.dataset(),
                configuration.rightGeometryColumnName(), configuration.usesFamilyGeometry()).alias(RIGHT_ALIAS);
        GeometryTypeDefinition geometryType = SpatialOverlayGeometrySupport.outputType(
                configuration, leftGeometry.geometry(), rightGeometry.geometry());
        Dataset<Row> result = switch (configuration.operation()) {
            case INTERSECTION -> intersection(
                    leftDataset, rightDataset, configuration, outputs, geometryType);
            case ERASE -> difference(
                    leftDataset, rightDataset, configuration, outputs, geometryType);
            case IDENTITY -> intersection(leftDataset, rightDataset, configuration, outputs, geometryType)
                    .unionByName(difference(leftDataset, rightDataset, configuration, outputs, geometryType));
            case UNION, SYMMETRICAL_DIFFERENCE -> {
                Dataset<Row> leftOnly = difference(
                        leftDataset, rightDataset, configuration, outputs, geometryType);
                SpatialOverlayConfiguration reversed = new SpatialOverlayConfiguration(
                        configuration.rightTableName(), configuration.rightGeometryColumnName(),
                        configuration.leftTableName(), configuration.leftGeometryColumnName(),
                        configuration.operation(), configuration.outputTableName(),
                        configuration.outputGeometryColumnName(), configuration.outputColumns(), configuration.geometryPolicy());
                Dataset<Row> rightOnly = differenceReversed(
                        rightDataset, leftDataset, reversed, outputs, geometryType);
                Dataset<Row> exclusive = leftOnly.unionByName(rightOnly);
                yield configuration.operation() == SpatialOverlayOperation.UNION
                        ? intersection(leftDataset, rightDataset, configuration, outputs, geometryType).unionByName(exclusive)
                        : exclusive;
            }
        };

        List<CanvasColumnSchema> schemaColumns = outputSchemaColumns(configuration, outputs, geometryType);
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, schemaColumns,
                CanvasDatasetKind.BOUNDED, null, null);
        result.schema();
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void requireConfiguration(
            SpatialOverlayConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.leftTableName(), "请选择左侧图层",
                "configuration.leftTableName", issues);
        CanvasNodeSupport.required(configuration.leftGeometryColumnName(), "请选择左侧 Geometry 字段",
                "configuration.leftGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.rightTableName(), "请选择右侧图层",
                "configuration.rightTableName", issues);
        CanvasNodeSupport.required(configuration.rightGeometryColumnName(), "请选择右侧 Geometry 字段",
                "configuration.rightGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        CanvasNodeSupport.required(configuration.outputGeometryColumnName(), "请输入结果 Geometry 字段名",
                "configuration.outputGeometryColumnName", issues);
        if (configuration.operation() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择叠加方式", "configuration.operation");
        }
        if (!CanvasNodeSupport.blank(configuration.leftTableName())
                && configuration.leftTableName().equals(configuration.rightTableName())) {
            issues.error("INVALID_JOIN_TABLE", "左右图层不能相同", "configuration.rightTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }
    }

    private static void validateTable(
            SparkCanvasTable table,
            String tableName,
            String label,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (!CanvasNodeSupport.blank(tableName) && table == null) {
            issues.error("TABLE_NOT_FOUND", label + "图层不在上游数据中：" + tableName, path);
        } else if (table != null && table.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "空间叠加只支持有界输入", path);
        }
    }

    private static CanvasColumnSchema validateGeometry(
            String name,
            Map<String, CanvasColumnSchema> columns,
            String label,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(name)) return null;
        CanvasColumnSchema column = columns.get(name);
        if (column == null) {
            issues.error("COLUMN_NOT_FOUND", label + " Geometry 字段不存在：" + name, path);
            return null;
        }
        if (column.fieldType() != PlatformDataType.GEOMETRY || column.geometry() == null) {
            issues.error("GEOMETRY_METADATA_REQUIRED", label + "字段不是完整的 Geometry", path);
            return null;
        }
        return column;
    }

    private static void validateGeometryPair(
            CanvasColumnSchema left,
            CanvasColumnSchema right,
            CanvasNodeIssueSink issues
    ) {
        if (left == null || right == null) return;
        if (!left.geometry().crs().equals(right.geometry().crs())) {
            issues.error("SPATIAL_OVERLAY_CRS_MISMATCH", "左右 Geometry CRS 不一致，请先进行空间转换",
                    "configuration.rightGeometryColumnName");
        }
        if (left.geometry().dimension() != right.geometry().dimension()) {
            issues.error("SPATIAL_OVERLAY_DIMENSION_MISMATCH", "左右 Geometry 坐标维度不一致",
                    "configuration.rightGeometryColumnName");
        }
    }

    private static void validateOutputColumns(
            SpatialOverlayConfiguration configuration,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputs,
            CanvasNodeIssueSink issues
    ) {
        Set<String> names = new HashSet<>();
        for (JoinOutputColumnSupport.ResolvedOutputColumn output : outputs) {
            names.add(output.outputColumnName().toLowerCase(Locale.ROOT));
            if (configuration.operation() == SpatialOverlayOperation.ERASE
                    && output.sourceSide() == JoinOutputColumnSource.RIGHT) {
                issues.error("SPATIAL_OVERLAY_ERASE_RIGHT_FIELD_UNSUPPORTED",
                        "擦除结果不能输出右侧属性", "configuration.outputColumns");
            }
        }
        if (!CanvasNodeSupport.blank(configuration.outputGeometryColumnName())
                && !names.add(configuration.outputGeometryColumnName().toLowerCase(Locale.ROOT))) {
            issues.error("DUPLICATE_COLUMN_NAME",
                    "结果 Geometry 字段与输出字段重名：" + configuration.outputGeometryColumnName(),
                    "configuration.outputGeometryColumnName");
        }
    }

    private static Dataset<Row> intersection(
            Dataset<Row> left,
            Dataset<Row> right,
            SpatialOverlayConfiguration configuration,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputs,
            GeometryTypeDefinition geometryType
    ) {
        Column leftGeometry = qualified(LEFT_ALIAS, configuration.leftGeometryColumnName());
        Column rightGeometry = qualified(RIGHT_ALIAS, configuration.rightGeometryColumnName());
        Dataset<Row> joined = left.join(right, st_predicates.ST_Intersects(leftGeometry, rightGeometry), "inner");
        Column resultGeometry = withSrid(
                st_functions.ST_Intersection(leftGeometry, rightGeometry), geometryType)
                .alias(configuration.outputGeometryColumnName());
        List<Column> projection = projectedAttributes(outputs);
        projection.add(resultGeometry);
        Dataset<Row> projected = joined.select(projection.toArray(Column[]::new));
        return nonEmpty(projected, configuration.outputGeometryColumnName());
    }

    private static Dataset<Row> difference(
            Dataset<Row> left,
            Dataset<Row> right,
            SpatialOverlayConfiguration configuration,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputs,
            GeometryTypeDefinition geometryType
    ) {
        String rowId = internalName(left, right, "__datascalpel_overlay_left_id");
        String mask = internalName(left, right, "__datascalpel_overlay_right_union");
        Dataset<Row> identified = left.withColumn(rowId, CatalystLineageMetadata.markTechnicalColumn(
                functions.monotonically_increasing_id(), rowId)).alias(LEFT_ALIAS);
        Column leftGeometry = qualified(LEFT_ALIAS, configuration.leftGeometryColumnName());
        Column rightGeometry = qualified(RIGHT_ALIAS, configuration.rightGeometryColumnName());
        Dataset<Row> matchedMasks = identified.join(
                right,
                st_predicates.ST_Intersects(leftGeometry, rightGeometry),
                "inner")
                .groupBy(qualified(LEFT_ALIAS, rowId))
                .agg(st_aggregates.ST_Union_Agg(rightGeometry).alias(mask))
                .alias(LEFT_MASK_ALIAS);
        // Sedona cannot index a spatial LEFT OUTER JOIN. Find real masks with an
        // indexed INNER JOIN, then restore unmatched source rows through an equality
        // join on the plan-local row identity.
        Dataset<Row> prepared = identified.join(
                matchedMasks,
                qualified(LEFT_ALIAS, rowId).eqNullSafe(qualified(LEFT_MASK_ALIAS, rowId)),
                "left_outer");
        Column originalGeometry = qualified(LEFT_ALIAS, configuration.leftGeometryColumnName());
        Column maskGeometry = qualified(LEFT_MASK_ALIAS, mask);
        Column resultGeometry = withSrid(
                functions.when(maskGeometry.isNull(), originalGeometry)
                        .otherwise(st_functions.ST_Difference(originalGeometry, maskGeometry)),
                geometryType).alias(configuration.outputGeometryColumnName());
        List<Column> projection = new ArrayList<>(outputs.size() + 1);
        for (JoinOutputColumnSupport.ResolvedOutputColumn output : outputs) {
            if (output.sourceSide() == JoinOutputColumnSource.LEFT) {
                projection.add(qualified(LEFT_ALIAS, output.sourceColumn().name()).alias(output.outputColumnName()));
            } else {
                projection.add(nullOf(right, output.sourceColumn().name()).alias(output.outputColumnName()));
            }
        }
        projection.add(resultGeometry);
        return nonEmpty(prepared.select(projection.toArray(Column[]::new)),
                configuration.outputGeometryColumnName());
    }

    private static Dataset<Row> differenceReversed(
            Dataset<Row> right,
            Dataset<Row> left,
            SpatialOverlayConfiguration reversed,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputs,
            GeometryTypeDefinition geometryType
    ) {
        String rowId = internalName(right, left, "__datascalpel_overlay_right_id");
        String mask = internalName(right, left, "__datascalpel_overlay_left_union");
        Dataset<Row> identified = right.withColumn(rowId, CatalystLineageMetadata.markTechnicalColumn(
                functions.monotonically_increasing_id(), rowId)).alias(RIGHT_ALIAS);
        Column rightGeometry = qualified(RIGHT_ALIAS, reversed.leftGeometryColumnName());
        Column leftGeometry = qualified(LEFT_ALIAS, reversed.rightGeometryColumnName());
        Dataset<Row> matchedMasks = identified.join(
                left,
                st_predicates.ST_Intersects(rightGeometry, leftGeometry),
                "inner")
                .groupBy(qualified(RIGHT_ALIAS, rowId))
                .agg(st_aggregates.ST_Union_Agg(leftGeometry).alias(mask))
                .alias(RIGHT_MASK_ALIAS);
        Dataset<Row> prepared = identified.join(
                matchedMasks,
                qualified(RIGHT_ALIAS, rowId).eqNullSafe(qualified(RIGHT_MASK_ALIAS, rowId)),
                "left_outer");
        Column originalGeometry = qualified(RIGHT_ALIAS, reversed.leftGeometryColumnName());
        Column maskGeometry = qualified(RIGHT_MASK_ALIAS, mask);
        Column resultGeometry = withSrid(
                functions.when(maskGeometry.isNull(), originalGeometry)
                        .otherwise(st_functions.ST_Difference(originalGeometry, maskGeometry)),
                geometryType).alias(reversed.outputGeometryColumnName());
        List<Column> projection = new ArrayList<>(outputs.size() + 1);
        for (JoinOutputColumnSupport.ResolvedOutputColumn output : outputs) {
            if (output.sourceSide() == JoinOutputColumnSource.RIGHT) {
                projection.add(qualified(RIGHT_ALIAS, output.sourceColumn().name()).alias(output.outputColumnName()));
            } else {
                projection.add(nullOf(left, output.sourceColumn().name()).alias(output.outputColumnName()));
            }
        }
        projection.add(resultGeometry);
        return nonEmpty(prepared.select(projection.toArray(Column[]::new)), reversed.outputGeometryColumnName());
    }

    private static List<Column> projectedAttributes(
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputs
    ) {
        List<Column> projection = new ArrayList<>(outputs.size() + 1);
        for (JoinOutputColumnSupport.ResolvedOutputColumn output : outputs) {
            String side = output.sourceSide() == JoinOutputColumnSource.LEFT ? LEFT_ALIAS : RIGHT_ALIAS;
            projection.add(qualified(side, output.sourceColumn().name()).alias(output.outputColumnName()));
        }
        return projection;
    }

    private static List<CanvasColumnSchema> outputSchemaColumns(
            SpatialOverlayConfiguration configuration,
            List<JoinOutputColumnSupport.ResolvedOutputColumn> outputs,
            GeometryTypeDefinition sourceGeometry
    ) {
        boolean bothNullable = configuration.operation() == SpatialOverlayOperation.UNION
                || configuration.operation() == SpatialOverlayOperation.SYMMETRICAL_DIFFERENCE;
        List<CanvasColumnSchema> columns = new ArrayList<>(outputs.size() + 1);
        for (JoinOutputColumnSupport.ResolvedOutputColumn output : outputs) {
            CanvasColumnSchema column = JoinOutputColumnSupport.copyWithName(
                    output.sourceColumn(), output.outputColumnName());
            boolean nullableSide = bothNullable || (configuration.operation() == SpatialOverlayOperation.IDENTITY
                    && output.sourceSide() == JoinOutputColumnSource.RIGHT);
            columns.add(nullableSide ? nullable(column) : column);
        }
        columns.add(new CanvasColumnSchema(
                configuration.outputGeometryColumnName(), PlatformDataType.GEOMETRY,
                null, null, null, false, null, false, false, null,
                sourceGeometry));
        return List.copyOf(columns);
    }

    private static CanvasColumnSchema nullable(CanvasColumnSchema source) {
        return new CanvasColumnSchema(
                source.name(), source.fieldType(), source.length(), source.precision(), source.scale(),
                true, source.defaultValue(), source.autoIncrement(), source.generated(),
                source.comment(), source.geometry());
    }

    private static Column withSrid(Column geometry, GeometryTypeDefinition type) {
        return SpatialOverlayGeometrySupport.result(geometry, type);
    }

    private static Dataset<Row> nonEmpty(Dataset<Row> dataset, String geometryColumn) {
        Column geometry = column(dataset, geometryColumn);
        return dataset.filter(geometry.isNotNull().and(functions.not(st_functions.ST_IsEmpty(geometry))));
    }

    private static Column nullOf(Dataset<Row> dataset, String columnName) {
        return functions.lit(null).cast(dataset.schema().apply(columnName).dataType());
    }

    private static Column column(Dataset<Row> dataset, String name) {
        return dataset.col(CanvasNodeSupport.quoteIdentifier(name));
    }

    private static Column qualified(String alias, String name) {
        return functions.col(alias + "." + CanvasNodeSupport.quoteIdentifier(name));
    }

    private static String internalName(Dataset<Row> left, Dataset<Row> right, String base) {
        Set<String> names = new HashSet<>();
        for (String name : left.columns()) names.add(name.toLowerCase(Locale.ROOT));
        for (String name : right.columns()) names.add(name.toLowerCase(Locale.ROOT));
        String candidate = base;
        while (names.contains(candidate.toLowerCase(Locale.ROOT))) candidate += "_";
        return candidate;
    }
}

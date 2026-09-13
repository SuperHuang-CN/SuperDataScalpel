package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialDescribeDatasetConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialDescribeDatasetNodeDefinition;
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
import org.apache.spark.sql.api.java.UDF1;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.functions;
import org.apache.spark.sql.sedona_sql.expressions.st_constructors;
import org.apache.spark.sql.sedona_sql.expressions.st_functions;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Bounded dataset profiling aligned with the ArcGIS GeoAnalytics Describe Dataset capability. */
public final class SpatialDescribeDatasetNodeOperator implements CanvasNodeOperator {

    private static final String RECORD_COUNT = "__datascalpel_describe_record_count";
    private static final Set<PlatformDataType> NUMERIC_TYPES = Set.of(
            PlatformDataType.BYTE,
            PlatformDataType.SHORT,
            PlatformDataType.INTEGER,
            PlatformDataType.LONG,
            PlatformDataType.FLOAT,
            PlatformDataType.DOUBLE,
            PlatformDataType.DECIMAL
    );
    private static final Set<PlatformDataType> TEMPORAL_TYPES = Set.of(
            PlatformDataType.DATE,
            PlatformDataType.TIMESTAMP,
            PlatformDataType.TIMESTAMP_NTZ
    );

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_DESCRIBE_DATASET;
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
        if (!(definition instanceof SpatialDescribeDatasetNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_DESCRIBE_DATASET operator received "
                    + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialDescribeDatasetConfiguration configuration = node.configuration();
        if (configuration == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        CanvasNodeIssueSink issues = context.issues();
        validateBase(configuration, inputs, issues);
        SparkCanvasTable source = CanvasNodeSupport.blank(configuration.sourceTableName())
                ? null : inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }
        if (source == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "描述数据集只支持有界输入",
                    "configuration.sourceTableName");
        }

        CanvasColumnSchema geometry = null;
        if (!CanvasNodeSupport.blank(configuration.geometryColumnName())) {
            geometry = CanvasNodeSupport.columns(source.schema()).get(configuration.geometryColumnName());
            if (geometry == null) {
                issues.error("COLUMN_NOT_FOUND", "Geometry 字段不存在：" + configuration.geometryColumnName(),
                        "configuration.geometryColumnName");
            } else if (geometry.fieldType() != PlatformDataType.GEOMETRY) {
                issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "所选字段不是 Geometry："
                                + configuration.geometryColumnName(),
                        "configuration.geometryColumnName");
                geometry = null;
            } else {
                CanvasNodeSupport.validateSupportedGeometry(
                        List.of(geometry), "configuration.geometryColumnName", issues);
            }
        }
        if (configuration.extentOutput() && CanvasNodeSupport.blank(configuration.geometryColumnName())) {
            issues.error("DESCRIBE_DATASET_EXTENT_GEOMETRY_REQUIRED", "输出范围时必须选择 Geometry 字段",
                    "configuration.geometryColumnName");
        }
        if (issues.hasErrors()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> sourceDataset = source.dataset();
        List<CanvasColumnSchema> statisticsSchemaColumns = statisticsColumns();
        List<CanvasColumnSchema> descriptionSchemaColumns = descriptionColumns();
        Dataset<Row> statistics = context.runtimeValues().preview()
                ? statisticsSchemaPlan(sourceDataset, source.schema().columns(), statisticsSchemaColumns)
                : statisticsPlan(sourceDataset, source.schema().columns());
        Dataset<Row> description = context.runtimeValues().preview()
                ? collectiveSchemaPlan(sourceDataset,
                        source.schema().columns().stream().map(CanvasColumnSchema::name).toList(),
                        descriptionSchemaColumns, "__datascalpel_describe_members")
                : descriptionPlan(sourceDataset, source.schema(), geometry);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);

        CanvasTableSchema statisticsSchema = new CanvasTableSchema(
                configuration.statisticsTableName(), null, statisticsSchemaColumns,
                CanvasDatasetKind.BOUNDED, null, null);
        output.put(statisticsSchema.name(), new SparkCanvasTable(statisticsSchema, statistics));

        CanvasTableSchema descriptionSchema = new CanvasTableSchema(
                configuration.descriptionTableName(), null, descriptionSchemaColumns,
                CanvasDatasetKind.BOUNDED, null, null);
        output.put(descriptionSchema.name(), new SparkCanvasTable(descriptionSchema, description));

        if (configuration.sampleSize() > 0) {
            CanvasTableSchema sampleSchema = new CanvasTableSchema(
                    configuration.sampleTableName(), null, source.schema().columns(), CanvasDatasetKind.BOUNDED,
                    source.schema().eventTimeColumn(), source.schema().watermarkDelay());
            output.put(sampleSchema.name(), new SparkCanvasTable(sampleSchema,
                    sourceDataset.limit(configuration.sampleSize())));
        }

        if (configuration.extentOutput()) {
            List<CanvasColumnSchema> extentSchemaColumns = extentColumns(geometry);
            Dataset<Row> extent = context.runtimeValues().preview()
                    ? collectiveSchemaPlan(sourceDataset,
                            List.of(configuration.geometryColumnName()), extentSchemaColumns,
                            "__datascalpel_describe_extent_members")
                    : extentPlan(sourceDataset, configuration.geometryColumnName(), geometry);
            CanvasTableSchema extentSchema = new CanvasTableSchema(
                    configuration.extentTableName(), null, extentSchemaColumns,
                    CanvasDatasetKind.BOUNDED, null, null);
            output.put(extentSchema.name(), new SparkCanvasTable(extentSchema, extent));
        }
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static Dataset<Row> statisticsSchemaPlan(
            Dataset<Row> source,
            List<CanvasColumnSchema> sourceColumns,
            List<CanvasColumnSchema> outputColumns
    ) {
        List<String> dependencies = sourceColumns.stream()
                .filter(column -> column.fieldType() != PlatformDataType.GEOMETRY
                        && column.fieldType() != PlatformDataType.BINARY)
                .map(CanvasColumnSchema::name)
                .toList();
        if (dependencies.isEmpty()) {
            dependencies = sourceColumns.stream().map(CanvasColumnSchema::name).toList();
        }
        return collectiveSchemaPlan(source, dependencies, outputColumns,
                "__datascalpel_describe_statistic_members");
    }

    private static Dataset<Row> collectiveSchemaPlan(
            Dataset<Row> source,
            List<String> dependencyColumns,
            List<CanvasColumnSchema> outputColumns,
            String temporaryBase
    ) {
        Set<String> occupied = new HashSet<>(List.of(source.columns()));
        String membersName = temporaryBase;
        while (!occupied.add(membersName)) membersName += "_";
        List<Column> dependencies = dependencyColumns.stream()
                .map(name -> source.col(CanvasNodeSupport.quoteIdentifier(name)))
                .toList();
        Column members = functions.collect_list(functions.struct(
                dependencies.toArray(Column[]::new))).over(Window.partitionBy());
        Dataset<Row> base = source.filter(functions.lit(false)).withColumn(membersName, members);
        Column collective = base.col(CanvasNodeSupport.quoteIdentifier(membersName));
        List<Column> projection = outputColumns.stream()
                .map(column -> previewValue(collective, SparkTypeMapper.toDataType(column))
                        .alias(column.name()))
                .toList();
        return base.select(projection.toArray(Column[]::new));
    }

    private static Column previewValue(Column dependency, org.apache.spark.sql.types.DataType type) {
        return functions.udf((UDF1<Object, Object>) ignored -> {
            throw new IllegalArgumentException("SPATIAL_DESCRIBE_DATASET_PREVIEW_NOT_EXECUTABLE");
        }, type).apply(dependency);
    }

    private static void validateBase(
            SpatialDescribeDatasetConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.statisticsTableName(), "请输入字段统计表名",
                "configuration.statisticsTableName", issues);
        CanvasNodeSupport.required(configuration.descriptionTableName(), "请输入数据集描述表名",
                "configuration.descriptionTableName", issues);
        if (configuration.sampleSize() < 0
                || configuration.sampleSize() > SpatialDescribeDatasetConfiguration.MAX_SAMPLE_SIZE) {
            issues.error("INVALID_DESCRIBE_DATASET_SAMPLE_SIZE", "样本数量必须在 0 到 10000 之间",
                    "configuration.sampleSize");
        }
        if (configuration.sampleSize() > 0) {
            if (CanvasNodeSupport.blank(configuration.sampleTableName())) {
                issues.error("DESCRIBE_DATASET_SAMPLE_TABLE_REQUIRED", "启用样本时必须输入样本表名",
                        "configuration.sampleTableName");
            }
        }
        if (configuration.extentOutput()) {
            if (CanvasNodeSupport.blank(configuration.extentTableName())) {
                issues.error("DESCRIBE_DATASET_EXTENT_TABLE_REQUIRED", "启用范围时必须输入范围表名",
                        "configuration.extentTableName");
            }
        }

        Set<String> occupied = new HashSet<>();
        inputs.keySet().stream().map(SpatialDescribeDatasetNodeOperator::normalized).forEach(occupied::add);
        validateOutputTableName(configuration.statisticsTableName(), "configuration.statisticsTableName",
                occupied, issues);
        validateOutputTableName(configuration.descriptionTableName(), "configuration.descriptionTableName",
                occupied, issues);
        if (configuration.sampleSize() > 0) {
            validateOutputTableName(configuration.sampleTableName(), "configuration.sampleTableName",
                    occupied, issues);
        }
        if (configuration.extentOutput()) {
            validateOutputTableName(configuration.extentTableName(), "configuration.extentTableName",
                    occupied, issues);
        }
    }

    private static void validateOutputTableName(
            String tableName,
            String path,
            Set<String> occupied,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(tableName)) {
            return;
        }
        if (!occupied.add(normalized(tableName))) {
            issues.error("DUPLICATE_TABLE_NAME", "结果表名重复或已被入口表占用：" + tableName, path);
        }
    }

    private static Dataset<Row> statisticsPlan(
            Dataset<Row> source,
            List<CanvasColumnSchema> sourceColumns
    ) {
        List<CanvasColumnSchema> profileColumns = sourceColumns.stream()
                .filter(column -> column.fieldType() != PlatformDataType.GEOMETRY
                        && column.fieldType() != PlatformDataType.BINARY)
                .toList();
        if (profileColumns.isEmpty()) {
            return emptyStatisticsPlan(source);
        }

        List<Column> aggregates = new ArrayList<>();
        aggregates.add(functions.count(functions.lit(1L)).alias(RECORD_COUNT));
        List<FieldAliases> aliases = new ArrayList<>();
        for (int index = 0; index < profileColumns.size(); index++) {
            CanvasColumnSchema field = profileColumns.get(index);
            Column value = source.col(CanvasNodeSupport.quoteIdentifier(field.name()));
            FieldAliases names = FieldAliases.of(index);
            aliases.add(names);
            aggregates.add(functions.count(value).alias(names.nonNullCount()));
            if (field.fieldType() == PlatformDataType.STRING
                    || field.fieldType() == PlatformDataType.BOOLEAN) {
                aggregates.add(functions.min(value.cast("string")).alias(names.anyValue()));
            }
            if (NUMERIC_TYPES.contains(field.fieldType())) {
                Column number = value.cast("double");
                aggregates.add(functions.sum(number).alias(names.numericSum()));
                aggregates.add(functions.avg(number).alias(names.numericMean()));
                aggregates.add(functions.min(number).alias(names.numericMinimum()));
                aggregates.add(functions.max(number).alias(names.numericMaximum()));
                aggregates.add(functions.stddev_pop(number).alias(names.numericStandardDeviation()));
                aggregates.add(functions.var_pop(number).alias(names.numericVariance()));
            }
            if (TEMPORAL_TYPES.contains(field.fieldType())) {
                aggregates.add(functions.min(value).cast("string").alias(names.temporalMinimum()));
                aggregates.add(functions.max(value).cast("string").alias(names.temporalMaximum()));
                aggregates.add(temporalRangeMillis(value).alias(names.temporalRangeMillis()));
            }
        }

        Dataset<Row> aggregate = aggregate(source, aggregates);
        List<Column> rows = new ArrayList<>();
        for (int index = 0; index < profileColumns.size(); index++) {
            CanvasColumnSchema field = profileColumns.get(index);
            FieldAliases names = aliases.get(index);
            Column numericMinimum = NUMERIC_TYPES.contains(field.fieldType())
                    ? aggregate.col(names.numericMinimum()) : nullDouble();
            Column numericMaximum = NUMERIC_TYPES.contains(field.fieldType())
                    ? aggregate.col(names.numericMaximum()) : nullDouble();
            rows.add(functions.struct(
                    functions.lit(field.name()).alias("field_name"),
                    functions.lit(field.fieldType().name()).alias("field_type"),
                    aggregate.col(names.nonNullCount()).cast("long").alias("non_null_count"),
                    aggregate.col(RECORD_COUNT).minus(aggregate.col(names.nonNullCount()))
                            .cast("long").alias("null_count"),
                    (field.fieldType() == PlatformDataType.STRING
                            || field.fieldType() == PlatformDataType.BOOLEAN
                            ? aggregate.col(names.anyValue()) : nullString()).alias("any_value"),
                    (NUMERIC_TYPES.contains(field.fieldType())
                            ? aggregate.col(names.numericSum()) : nullDouble()).alias("numeric_sum"),
                    (NUMERIC_TYPES.contains(field.fieldType())
                            ? aggregate.col(names.numericMean()) : nullDouble()).alias("numeric_mean"),
                    numericMinimum.alias("numeric_minimum"),
                    numericMaximum.alias("numeric_maximum"),
                    (NUMERIC_TYPES.contains(field.fieldType())
                            ? numericMaximum.minus(numericMinimum) : nullDouble()).alias("numeric_range"),
                    (NUMERIC_TYPES.contains(field.fieldType())
                            ? aggregate.col(names.numericStandardDeviation()) : nullDouble())
                            .alias("numeric_standard_deviation"),
                    (NUMERIC_TYPES.contains(field.fieldType())
                            ? aggregate.col(names.numericVariance()) : nullDouble()).alias("numeric_variance"),
                    (TEMPORAL_TYPES.contains(field.fieldType())
                            ? aggregate.col(names.temporalMinimum()) : nullString()).alias("temporal_minimum"),
                    (TEMPORAL_TYPES.contains(field.fieldType())
                            ? aggregate.col(names.temporalMaximum()) : nullString()).alias("temporal_maximum"),
                    (TEMPORAL_TYPES.contains(field.fieldType())
                            ? aggregate.col(names.temporalRangeMillis()) : nullLong()).alias("temporal_range_millis")
            ));
        }
        return aggregate.select(functions.explode(functions.array(rows.toArray(Column[]::new))).alias("statistic"))
                .select("statistic.*");
    }

    private static Dataset<Row> emptyStatisticsPlan(Dataset<Row> source) {
        return source.limit(0).select(
                nullString().alias("field_name"),
                nullString().alias("field_type"),
                nullLong().alias("non_null_count"),
                nullLong().alias("null_count"),
                nullString().alias("any_value"),
                nullDouble().alias("numeric_sum"),
                nullDouble().alias("numeric_mean"),
                nullDouble().alias("numeric_minimum"),
                nullDouble().alias("numeric_maximum"),
                nullDouble().alias("numeric_range"),
                nullDouble().alias("numeric_standard_deviation"),
                nullDouble().alias("numeric_variance"),
                nullString().alias("temporal_minimum"),
                nullString().alias("temporal_maximum"),
                nullLong().alias("temporal_range_millis")
        );
    }

    private static Dataset<Row> descriptionPlan(
            Dataset<Row> source,
            CanvasTableSchema sourceSchema,
            CanvasColumnSchema geometry
    ) {
        List<Column> aggregates = new ArrayList<>();
        aggregates.add(functions.count(functions.lit(1L)).alias("record_count"));

        Column nonEmptyGeometry = null;
        if (geometry != null) {
            Column value = source.col(CanvasNodeSupport.quoteIdentifier(geometry.name()));
            nonEmptyGeometry = value.isNotNull().and(functions.not(st_functions.ST_IsEmpty(value)));
            aggregates.add(functions.coalesce(
                    functions.sum(functions.when(nonEmptyGeometry, functions.lit(1L)).otherwise(functions.lit(0L))),
                    functions.lit(0L)).cast("long").alias("geometry_non_empty_count"));
            aggregates.add(functions.min(functions.when(nonEmptyGeometry, st_functions.ST_XMin(value)))
                    .cast("double").alias("extent_x_minimum"));
            aggregates.add(functions.min(functions.when(nonEmptyGeometry, st_functions.ST_YMin(value)))
                    .cast("double").alias("extent_y_minimum"));
            aggregates.add(functions.max(functions.when(nonEmptyGeometry, st_functions.ST_XMax(value)))
                    .cast("double").alias("extent_x_maximum"));
            aggregates.add(functions.max(functions.when(nonEmptyGeometry, st_functions.ST_YMax(value)))
                    .cast("double").alias("extent_y_maximum"));
        }

        CanvasColumnSchema eventTime = sourceSchema.eventTimeColumn() == null ? null
                : sourceSchema.columns().stream()
                .filter(column -> column.name().equals(sourceSchema.eventTimeColumn()))
                .findFirst().orElse(null);
        if (eventTime != null) {
            Column value = source.col(CanvasNodeSupport.quoteIdentifier(eventTime.name()));
            aggregates.add(functions.count(value).cast("long").alias("event_time_non_null_count"));
            aggregates.add(functions.min(value).cast("string").alias("event_time_minimum"));
            aggregates.add(functions.max(value).cast("string").alias("event_time_maximum"));
            aggregates.add(temporalRangeMillis(value).alias("event_time_range_millis"));
        }

        Dataset<Row> aggregate = aggregate(source, aggregates);
        List<Column> output = new ArrayList<>();
        output.add(functions.lit(sourceSchema.name()).alias("dataset_name"));
        output.add(aggregate.col("record_count").cast("long").alias("record_count"));
        output.add(functions.lit(sourceSchema.columns().size()).cast("integer").alias("field_count"));
        if (geometry == null) {
            output.add(nullString().alias("geometry_column_name"));
            output.add(nullString().alias("geometry_kind"));
            output.add(nullString().alias("geometry_crs"));
            output.add(nullString().alias("geometry_dimension"));
            output.add(nullLong().alias("geometry_non_empty_count"));
            output.add(nullLong().alias("geometry_null_or_empty_count"));
            output.add(nullDouble().alias("extent_x_minimum"));
            output.add(nullDouble().alias("extent_y_minimum"));
            output.add(nullDouble().alias("extent_x_maximum"));
            output.add(nullDouble().alias("extent_y_maximum"));
        } else {
            GeometryTypeDefinition type = geometry.geometry();
            output.add(functions.lit(geometry.name()).alias("geometry_column_name"));
            output.add(functions.lit(type.kind().name()).alias("geometry_kind"));
            output.add(functions.lit(type.crs().authority() + ":" + type.crs().code()).alias("geometry_crs"));
            output.add(functions.lit(type.dimension().name()).alias("geometry_dimension"));
            output.add(aggregate.col("geometry_non_empty_count").alias("geometry_non_empty_count"));
            output.add(aggregate.col("record_count").minus(aggregate.col("geometry_non_empty_count"))
                    .cast("long").alias("geometry_null_or_empty_count"));
            output.add(aggregate.col("extent_x_minimum"));
            output.add(aggregate.col("extent_y_minimum"));
            output.add(aggregate.col("extent_x_maximum"));
            output.add(aggregate.col("extent_y_maximum"));
        }
        if (eventTime == null) {
            output.add(nullString().alias("event_time_column_name"));
            output.add(nullLong().alias("event_time_non_null_count"));
            output.add(nullLong().alias("event_time_null_count"));
            output.add(nullString().alias("event_time_minimum"));
            output.add(nullString().alias("event_time_maximum"));
            output.add(nullLong().alias("event_time_range_millis"));
        } else {
            output.add(functions.lit(eventTime.name()).alias("event_time_column_name"));
            output.add(aggregate.col("event_time_non_null_count"));
            output.add(aggregate.col("record_count").minus(aggregate.col("event_time_non_null_count"))
                    .cast("long").alias("event_time_null_count"));
            output.add(aggregate.col("event_time_minimum"));
            output.add(aggregate.col("event_time_maximum"));
            output.add(aggregate.col("event_time_range_millis"));
        }
        Dataset<Row> base = aggregate.select(output.toArray(Column[]::new));
        Column[] jsonFields = java.util.Arrays.stream(base.columns())
                .map(name -> base.col(CanvasNodeSupport.quoteIdentifier(name)).alias(name))
                .toArray(Column[]::new);
        return base.withColumn("description_json", functions.to_json(functions.struct(jsonFields)));
    }

    private static Dataset<Row> extentPlan(
            Dataset<Row> source,
            String geometryColumnName,
            CanvasColumnSchema geometry
    ) {
        Column value = source.col(CanvasNodeSupport.quoteIdentifier(geometryColumnName));
        Column nonEmpty = value.isNotNull().and(functions.not(st_functions.ST_IsEmpty(value)));
        Dataset<Row> bounds = source.filter(nonEmpty).agg(
                functions.min(st_functions.ST_XMin(value)).alias("x_minimum"),
                functions.min(st_functions.ST_YMin(value)).alias("y_minimum"),
                functions.max(st_functions.ST_XMax(value)).alias("x_maximum"),
                functions.max(st_functions.ST_YMax(value)).alias("y_maximum")
        ).filter(functions.col("x_minimum").isNotNull());
        Column polygon = st_constructors.ST_PolygonFromEnvelope(
                bounds.col("x_minimum"), bounds.col("y_minimum"),
                bounds.col("x_maximum"), bounds.col("y_maximum"));
        return bounds.select(st_functions.ST_SetSRID(polygon,
                functions.lit(geometry.geometry().crs().code())).alias("geometry"));
    }

    private static Dataset<Row> aggregate(Dataset<Row> source, List<Column> expressions) {
        return source.agg(expressions.getFirst(), expressions.subList(1, expressions.size()).toArray(Column[]::new));
    }

    private static Column temporalRangeMillis(Column value) {
        Column minimum = functions.min(value.cast("timestamp")).cast("double");
        Column maximum = functions.max(value.cast("timestamp")).cast("double");
        return maximum.minus(minimum).multiply(1000d).cast("long");
    }

    private static List<CanvasColumnSchema> statisticsColumns() {
        return List.of(
                scalar("field_name", PlatformDataType.STRING, false),
                scalar("field_type", PlatformDataType.STRING, false),
                scalar("non_null_count", PlatformDataType.LONG, false),
                scalar("null_count", PlatformDataType.LONG, false),
                scalar("any_value", PlatformDataType.STRING, true),
                scalar("numeric_sum", PlatformDataType.DOUBLE, true),
                scalar("numeric_mean", PlatformDataType.DOUBLE, true),
                scalar("numeric_minimum", PlatformDataType.DOUBLE, true),
                scalar("numeric_maximum", PlatformDataType.DOUBLE, true),
                scalar("numeric_range", PlatformDataType.DOUBLE, true),
                scalar("numeric_standard_deviation", PlatformDataType.DOUBLE, true),
                scalar("numeric_variance", PlatformDataType.DOUBLE, true),
                scalar("temporal_minimum", PlatformDataType.STRING, true),
                scalar("temporal_maximum", PlatformDataType.STRING, true),
                scalar("temporal_range_millis", PlatformDataType.LONG, true)
        );
    }

    private static List<CanvasColumnSchema> descriptionColumns() {
        return List.of(
                scalar("dataset_name", PlatformDataType.STRING, false),
                scalar("record_count", PlatformDataType.LONG, false),
                scalar("field_count", PlatformDataType.INTEGER, false),
                scalar("geometry_column_name", PlatformDataType.STRING, true),
                scalar("geometry_kind", PlatformDataType.STRING, true),
                scalar("geometry_crs", PlatformDataType.STRING, true),
                scalar("geometry_dimension", PlatformDataType.STRING, true),
                scalar("geometry_non_empty_count", PlatformDataType.LONG, true),
                scalar("geometry_null_or_empty_count", PlatformDataType.LONG, true),
                scalar("extent_x_minimum", PlatformDataType.DOUBLE, true),
                scalar("extent_y_minimum", PlatformDataType.DOUBLE, true),
                scalar("extent_x_maximum", PlatformDataType.DOUBLE, true),
                scalar("extent_y_maximum", PlatformDataType.DOUBLE, true),
                scalar("event_time_column_name", PlatformDataType.STRING, true),
                scalar("event_time_non_null_count", PlatformDataType.LONG, true),
                scalar("event_time_null_count", PlatformDataType.LONG, true),
                scalar("event_time_minimum", PlatformDataType.STRING, true),
                scalar("event_time_maximum", PlatformDataType.STRING, true),
                scalar("event_time_range_millis", PlatformDataType.LONG, true),
                scalar("description_json", PlatformDataType.STRING, false)
        );
    }

    private static List<CanvasColumnSchema> extentColumns(CanvasColumnSchema sourceGeometry) {
        GeometryTypeDefinition sourceType = sourceGeometry.geometry();
        return List.of(new CanvasColumnSchema(
                "geometry", PlatformDataType.GEOMETRY, null, null, null, false,
                null, false, false, "所选 Geometry 的 XY Envelope",
                new GeometryTypeDefinition(GeometryKind.POLYGON, sourceType.crs(), CoordinateDimension.XY)
        ));
    }

    private static CanvasColumnSchema scalar(String name, PlatformDataType type, boolean nullable) {
        return new CanvasColumnSchema(name, type, null, null, null, nullable,
                null, false, false, null);
    }

    private static Column nullString() {
        return functions.lit(null).cast("string");
    }

    private static Column nullLong() {
        return functions.lit(null).cast("long");
    }

    private static Column nullDouble() {
        return functions.lit(null).cast("double");
    }

    private static String normalized(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record FieldAliases(
            String nonNullCount,
            String anyValue,
            String numericSum,
            String numericMean,
            String numericMinimum,
            String numericMaximum,
            String numericStandardDeviation,
            String numericVariance,
            String temporalMinimum,
            String temporalMaximum,
            String temporalRangeMillis
    ) {
        private static FieldAliases of(int index) {
            String prefix = "__datascalpel_describe_field_" + index + "_";
            return new FieldAliases(
                    prefix + "count", prefix + "any", prefix + "sum", prefix + "mean",
                    prefix + "min", prefix + "max", prefix + "stddev", prefix + "variance",
                    prefix + "time_min", prefix + "time_max", prefix + "time_range"
            );
        }
    }
}

package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsConfiguration;
import cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TrackIncidentResultMode;
import cn.superhuang.data.scalpel.contract.task.TrackIncidentSemantics;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;
import org.apache.spark.sql.functions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TrackDetectIncidentsNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TRACK_DETECT_INCIDENTS;
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
        if (!(definition instanceof TrackDetectIncidentsNodeDefinition node)) {
            throw new IllegalArgumentException("TRACK_DETECT_INCIDENTS operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        TrackDetectIncidentsConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        CanvasNodeIssueSink issues = context.issues();
        validateConfiguration(configuration, inputs, issues);
        SparkCanvasTable source = inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }
        TrackNodeSupport.PreparedTrack prepared = TrackNodeSupport.prepare(
                source, configuration.pointGeometryColumnName(), false,
                configuration.trackIdColumns(), configuration.timeColumnName(),
                configuration.distanceMethod(),
                configuration.boundaries(), issues, "configuration",
                configuration.effectiveIncidentSemantics() == TrackIncidentSemantics.CONDITION_LIFECYCLE
                        ? configuration.orderByColumns() : null);
        SparkCanvasTable conditionSource = IncidentWindowPlan.prepare(configuration, source, prepared, issues);
        if (configuration.startCondition() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请配置事件开始条件", "configuration.startCondition");
        } else {
            CanvasPredicateExpressionBuilder.validate(
                    configuration.startCondition(), conditionSource, issues, "configuration.startCondition");
        }
        if (configuration.endCondition() != null) {
            CanvasPredicateExpressionBuilder.validate(
                    configuration.endCondition(), conditionSource, issues, "configuration.endCondition");
        }
        validateOutputNames(configuration, source, issues);
        if (issues.hasErrors() || prepared == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        if (configuration.effectiveIncidentSemantics() == TrackIncidentSemantics.CONDITION_LIFECYCLE) {
            return applyLifecycle(configuration, source, prepared, conditionSource.dataset(), inputs);
        }

        Dataset<Row> dataset = prepared.dataset();
        List<Column> partitions = new ArrayList<>();
        for (CanvasColumnSchema id : prepared.trackIdSchemas()) partitions.add(TrackNodeSupport.column(dataset, id.name()));
        partitions.add(TrackNodeSupport.column(dataset, prepared.segmentColumnName()));
        WindowSpec ordered = Window.partitionBy(partitions.toArray(Column[]::new))
                .orderBy(TrackNodeSupport.column(dataset, configuration.timeColumnName()).asc());
        WindowSpec cumulative = ordered.rowsBetween(Window.unboundedPreceding(), Window.currentRow());
        String startMatchName = TrackNodeSupport.internalName(dataset, "__datascalpel_incident_start_match");
        String endMatchName = TrackNodeSupport.internalName(dataset, "__datascalpel_incident_end_match");
        String startTriggerName = TrackNodeSupport.internalName(dataset, "__datascalpel_incident_start_trigger");
        String startSequenceName = TrackNodeSupport.internalName(dataset, "__datascalpel_incident_sequence");
        String rowOrdinalName = TrackNodeSupport.internalName(dataset, "__datascalpel_incident_row_ordinal");
        String lastStartOrdinalName = TrackNodeSupport.internalName(dataset, "__datascalpel_incident_last_start");
        String priorEndOrdinalName = TrackNodeSupport.internalName(dataset, "__datascalpel_incident_prior_end");
        String activeName = TrackNodeSupport.internalName(dataset, "__datascalpel_incident_active");
        Dataset<Row> staged = dataset
                .withColumn(startMatchName, CanvasPredicateExpressionBuilder.expression(
                        configuration.startCondition(), dataset));
        Column endCondition = configuration.endCondition() == null
                ? functions.lit(false)
                : CanvasPredicateExpressionBuilder.expression(configuration.endCondition(), dataset);
        staged = staged.withColumn(endMatchName, endCondition);
        Column previousStart = functions.lag(TrackNodeSupport.column(staged, startMatchName), 1).over(ordered);
        staged = staged.withColumn(startTriggerName, functions.when(
                TrackNodeSupport.column(staged, startMatchName)
                        .and(functions.coalesce(previousStart, functions.lit(false)).equalTo(false)), 1).otherwise(0));
        staged = staged.withColumn(startSequenceName,
                functions.sum(TrackNodeSupport.column(staged, startTriggerName)).over(cumulative));
        staged = staged.withColumn(rowOrdinalName, functions.row_number().over(ordered));
        staged = staged.withColumn(lastStartOrdinalName,
                functions.max(functions.when(
                        TrackNodeSupport.column(staged, startTriggerName).equalTo(1),
                        TrackNodeSupport.column(staged, rowOrdinalName))).over(cumulative));
        WindowSpec beforeCurrent = ordered.rowsBetween(Window.unboundedPreceding(), -1);
        staged = staged.withColumn(priorEndOrdinalName,
                functions.max(functions.when(
                        TrackNodeSupport.column(staged, endMatchName),
                        TrackNodeSupport.column(staged, rowOrdinalName))).over(beforeCurrent));
        Column lastStartOrdinal = TrackNodeSupport.column(staged, lastStartOrdinalName);
        Column priorEndOrdinal = TrackNodeSupport.column(staged, priorEndOrdinalName);
        Column active = lastStartOrdinal.isNotNull()
                .and(priorEndOrdinal.isNull().or(lastStartOrdinal.gt(priorEndOrdinal)));
        if (configuration.endCondition() == null) {
            active = TrackNodeSupport.column(staged, startSequenceName).gt(0);
        }
        staged = staged.withColumn(activeName, active);

        List<Column> incidentPartitions = new ArrayList<>(partitions);
        incidentPartitions.add(TrackNodeSupport.column(staged, startSequenceName));
        WindowSpec incidentWindow = Window.partitionBy(incidentPartitions.toArray(Column[]::new));
        Column time = TrackNodeSupport.column(staged, configuration.timeColumnName());
        Column activeColumn = TrackNodeSupport.column(staged, activeName);
        Column startTime = functions.min(functions.when(activeColumn, time)).over(incidentWindow);
        Column endTime = functions.max(functions.when(activeColumn, time)).over(incidentWindow);
        List<Column> idParts = new ArrayList<>();
        for (CanvasColumnSchema id : prepared.trackIdSchemas()) {
            idParts.add(TrackNodeSupport.column(staged, id.name()).cast("string"));
        }
        idParts.add(TrackNodeSupport.column(staged, prepared.segmentColumnName()).cast("string"));
        idParts.add(TrackNodeSupport.column(staged, startSequenceName).cast("string"));
        Column incidentId = functions.when(activeColumn,
                functions.sha2(functions.concat_ws("|", idParts.toArray(Column[]::new)), 256));
        Column duration = functions.when(activeColumn,
                functions.unix_micros(endTime).minus(functions.unix_micros(startTime))
                        .divide(functions.lit(1000d * TrackNodeSupport.millisPerUnit(
                                configuration.incidentDurationUnit()))));

        List<Column> projection = new ArrayList<>();
        for (CanvasColumnSchema column : source.schema().columns()) {
            projection.add(TrackNodeSupport.column(staged, column.name()));
        }
        projection.add(incidentId.alias(configuration.incidentIdColumnName()));
        projection.add(activeColumn.alias(configuration.incidentFlagColumnName()));
        projection.add(functions.when(activeColumn, startTime).alias(configuration.incidentStartTimeColumnName()));
        projection.add(functions.when(activeColumn, endTime).alias(configuration.incidentEndTimeColumnName()));
        projection.add(duration.alias(configuration.incidentDurationColumnName()));
        Dataset<Row> result = staged.select(projection.toArray(Column[]::new));
        if (configuration.resultMode() == TrackIncidentResultMode.INCIDENTS_ONLY) {
            result = result.filter(TrackNodeSupport.column(result, configuration.incidentFlagColumnName()));
        }

        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        outputColumns.add(new CanvasColumnSchema(configuration.incidentIdColumnName(),
                cn.superhuang.data.scalpel.contract.type.PlatformDataType.STRING,
                64, null, null, true, null, false, false, null, null));
        outputColumns.add(TrackNodeSupport.booleanColumn(configuration.incidentFlagColumnName(), false));
        outputColumns.add(TrackNodeSupport.timestampColumn(configuration.incidentStartTimeColumnName(), true));
        outputColumns.add(TrackNodeSupport.timestampColumn(configuration.incidentEndTimeColumnName(), true));
        outputColumns.add(TrackNodeSupport.doubleColumn(configuration.incidentDurationColumnName(), true));
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                CanvasDatasetKind.BOUNDED, source.schema().eventTimeColumn(), null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static CanvasNodeOperationResult applyLifecycle(
            TrackDetectIncidentsConfiguration configuration,
            SparkCanvasTable source,
            TrackNodeSupport.PreparedTrack prepared,
            Dataset<Row> data,
            Map<String, SparkCanvasTable> inputs
    ) {
        List<String> partitionNames = new ArrayList<>(configuration.trackIdColumns());
        partitionNames.add(prepared.segmentColumnName());
        List<Column> order = new ArrayList<>();
        order.add(TrackNodeSupport.column(data, configuration.timeColumnName()).asc());
        for (String name : configuration.orderByColumns()) order.add(TrackNodeSupport.column(data, name).asc_nulls_first());
        // Use quoted identifiers throughout: logical columns may contain punctuation.
        WindowSpec ordered = Window.partitionBy(partitionNames.stream()
                        .map(CanvasNodeSupport::quoteIdentifier).map(functions::col).toArray(Column[]::new))
                .orderBy(order.toArray(Column[]::new));
        WindowSpec cumulative = ordered.rowsBetween(Window.unboundedPreceding(), Window.currentRow());
        String start = TrackNodeSupport.internalName(data, "__datascalpel_lifecycle_start");
        String stop = TrackNodeSupport.internalName(data, "__datascalpel_lifecycle_stop");
        String block = TrackNodeSupport.internalName(data, "__datascalpel_lifecycle_block");
        String seenStart = TrackNodeSupport.internalName(data, "__datascalpel_lifecycle_seen_start");
        String active = TrackNodeSupport.internalName(data, "__datascalpel_lifecycle_active");
        String previous = TrackNodeSupport.internalName(data, "__datascalpel_lifecycle_previous");
        String status = TrackNodeSupport.internalName(data, "__datascalpel_lifecycle_status");
        String sequence = TrackNodeSupport.internalName(data, "__datascalpel_lifecycle_sequence");
        data = data.withColumn(start, functions.coalesce(
                CanvasPredicateExpressionBuilder.expression(configuration.startCondition(), data), functions.lit(false)));
        Column stopExpression = configuration.endCondition() == null
                ? TrackNodeSupport.column(data, start).equalTo(false)
                : functions.coalesce(CanvasPredicateExpressionBuilder.expression(configuration.endCondition(), data),
                        functions.lit(false));
        data = data.withColumn(stop, stopExpression);
        // Each stop opens a new block. Only the first eligible start in that block activates an incident.
        data = data.withColumn(block, functions.sum(functions.when(TrackNodeSupport.column(data, stop), 1L)
                .otherwise(0L)).over(cumulative));
        List<Column> blockPartitions = new ArrayList<>();
        for (String name : partitionNames) blockPartitions.add(TrackNodeSupport.column(data, name));
        blockPartitions.add(TrackNodeSupport.column(data, block));
        WindowSpec blockHistory = Window.partitionBy(blockPartitions.toArray(Column[]::new))
                .orderBy(order.toArray(Column[]::new))
                .rowsBetween(Window.unboundedPreceding(), Window.currentRow());
        data = data.withColumn(seenStart, functions.max(functions.when(
                TrackNodeSupport.column(data, start).and(TrackNodeSupport.column(data, stop).equalTo(false)), 1)
                .otherwise(0)).over(blockHistory));
        data = data.withColumn(active, TrackNodeSupport.column(data, seenStart).equalTo(1)
                .and(TrackNodeSupport.column(data, stop).equalTo(false)));
        data = data.withColumn(previous, functions.coalesce(
                functions.lag(TrackNodeSupport.column(data, active), 1).over(ordered), functions.lit(false)));
        Column started = TrackNodeSupport.column(data, active).and(TrackNodeSupport.column(data, previous).equalTo(false));
        Column ended = TrackNodeSupport.column(data, active).equalTo(false).and(TrackNodeSupport.column(data, previous));
        data = data.withColumn(status, functions.when(started, "Started")
                .when(ended, "Ended").when(TrackNodeSupport.column(data, active), "OnGoing"));
        data = data.withColumn(sequence, functions.sum(functions.when(
                TrackNodeSupport.column(data, status).equalTo("Started"), 1L).otherwise(0L)).over(cumulative));

        List<Column> incidentPartitions = new ArrayList<>();
        for (String name : partitionNames) incidentPartitions.add(TrackNodeSupport.column(data, name));
        incidentPartitions.add(TrackNodeSupport.column(data, sequence));
        WindowSpec incident = Window.partitionBy(incidentPartitions.toArray(Column[]::new));
        Column eventTime = TrackNodeSupport.column(data, configuration.timeColumnName());
        Column eventStatus = TrackNodeSupport.column(data, status);
        Column hasIncident = eventStatus.isNotNull();
        Column startTime = functions.min(functions.when(eventStatus.equalTo("Started"), eventTime)).over(incident);
        Column endTime = functions.max(functions.when(eventStatus.equalTo("Ended"), eventTime)).over(incident);
        // JSON struct avoids collisions from delimiters and null track identifiers.
        List<Column> identity = new ArrayList<>();
        for (int index = 0; index < configuration.trackIdColumns().size(); index++) {
            identity.add(TrackNodeSupport.column(data, configuration.trackIdColumns().get(index)).alias("track_" + index));
        }
        identity.add(TrackNodeSupport.column(data, prepared.segmentColumnName()).alias("segment"));
        identity.add(TrackNodeSupport.column(data, sequence).alias("incident"));
        Column id = functions.when(hasIncident, functions.sha2(
                functions.to_json(functions.struct(identity.toArray(Column[]::new))), 256));
        Column duration = functions.when(hasIncident, functions.unix_micros(eventTime)
                .minus(functions.unix_micros(startTime))
                .divide(1000d * TrackNodeSupport.millisPerUnit(configuration.incidentDurationUnit())));
        List<Column> projection = new ArrayList<>();
        for (CanvasColumnSchema column : source.schema().columns()) projection.add(TrackNodeSupport.column(data, column.name()));
        projection.add(id.alias(configuration.incidentIdColumnName()));
        projection.add(TrackNodeSupport.column(data, active).alias(configuration.incidentFlagColumnName()));
        projection.add(eventStatus.alias(configuration.incidentStatusColumnName()));
        projection.add(functions.when(hasIncident, startTime).alias(configuration.incidentStartTimeColumnName()));
        projection.add(functions.when(hasIncident, endTime).alias(configuration.incidentEndTimeColumnName()));
        projection.add(duration.alias(configuration.incidentDurationColumnName()));
        Dataset<Row> result = data.select(projection.toArray(Column[]::new));
        if (configuration.resultMode() == TrackIncidentResultMode.INCIDENTS_ONLY) {
            result = result.filter(TrackNodeSupport.column(result, configuration.incidentFlagColumnName()));
        }
        List<CanvasColumnSchema> columns = new ArrayList<>(source.schema().columns());
        columns.add(new CanvasColumnSchema(configuration.incidentIdColumnName(),
                cn.superhuang.data.scalpel.contract.type.PlatformDataType.STRING,
                64, null, null, true, null, false, false, null, null));
        columns.add(TrackNodeSupport.booleanColumn(configuration.incidentFlagColumnName(), false));
        columns.add(new CanvasColumnSchema(configuration.incidentStatusColumnName(),
                cn.superhuang.data.scalpel.contract.type.PlatformDataType.STRING,
                16, null, null, true, null, false, false, null, null));
        columns.add(TrackNodeSupport.timestampColumn(configuration.incidentStartTimeColumnName(), true));
        columns.add(TrackNodeSupport.timestampColumn(configuration.incidentEndTimeColumnName(), true));
        columns.add(TrackNodeSupport.doubleColumn(configuration.incidentDurationColumnName(), true));
        CanvasTableSchema schema = new CanvasTableSchema(configuration.outputTableName(), null, columns,
                CanvasDatasetKind.BOUNDED, source.schema().eventTimeColumn(), null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(schema.name(), new SparkCanvasTable(schema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateConfiguration(
            TrackDetectIncidentsConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表", "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.timeColumnName(), "请选择时间字段", "configuration.timeColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名", "configuration.outputTableName", issues);
        CanvasNodeSupport.required(configuration.incidentIdColumnName(), "请输入事件 ID 字段名",
                "configuration.incidentIdColumnName", issues);
        CanvasNodeSupport.required(configuration.incidentFlagColumnName(), "请输入事件标记字段名",
                "configuration.incidentFlagColumnName", issues);
        if (configuration.effectiveIncidentSemantics() == TrackIncidentSemantics.CONDITION_LIFECYCLE) {
            CanvasNodeSupport.required(configuration.incidentStatusColumnName(), "请输入事件状态字段名",
                    "configuration.incidentStatusColumnName", issues);
        }
        CanvasNodeSupport.required(configuration.incidentStartTimeColumnName(), "请输入事件开始字段名",
                "configuration.incidentStartTimeColumnName", issues);
        CanvasNodeSupport.required(configuration.incidentEndTimeColumnName(), "请输入事件结束字段名",
                "configuration.incidentEndTimeColumnName", issues);
        CanvasNodeSupport.required(configuration.incidentDurationColumnName(), "请输入事件持续时间字段名",
                "configuration.incidentDurationColumnName", issues);
        if (configuration.resultMode() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择事件结果范围", "configuration.resultMode");
        }
        if (configuration.incidentDurationUnit() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择事件持续时间单位", "configuration.incidentDurationUnit");
        }
        if (!CanvasNodeSupport.blank(configuration.outputTableName()) && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }
    }

    private static void validateOutputNames(
            TrackDetectIncidentsConfiguration configuration,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues
    ) {
        if (source == null) return;
        Set<String> names = new HashSet<>();
        for (CanvasColumnSchema column : source.schema().columns()) names.add(column.name().toLowerCase(Locale.ROOT));
        List<String> outputNames = new ArrayList<>(Arrays.asList(
                configuration.incidentIdColumnName(), configuration.incidentFlagColumnName(),
                configuration.incidentStartTimeColumnName(), configuration.incidentEndTimeColumnName(),
                configuration.incidentDurationColumnName()));
        if (configuration.effectiveIncidentSemantics() == TrackIncidentSemantics.CONDITION_LIFECYCLE) {
            outputNames.add(configuration.incidentStatusColumnName());
        }
        for (String name : outputNames) {
            if (!CanvasNodeSupport.blank(name) && !names.add(name.toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_COLUMN_NAME", "事件输出字段名重复：" + name, "configuration");
            }
        }
    }
}

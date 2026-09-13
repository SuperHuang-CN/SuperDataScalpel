package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.TraceProximityEntityOfInterest;
import cn.superhuang.data.scalpel.contract.task.TraceProximityEventsConfiguration;
import cn.superhuang.data.scalpel.contract.task.TraceProximityEventsNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TraceProximityInterestSource;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** ArcGIS-style temporal propagation from selected entities through Point proximity events. */
public final class TraceProximityEventsNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.TRACE_PROXIMITY_EVENTS;
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
        if (!(definition instanceof TraceProximityEventsNodeDefinition node)) {
            throw new IllegalArgumentException("TRACE_PROXIMITY_EVENTS operator received "
                    + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        TraceProximityEventsConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        CanvasNodeIssueSink issues = context.issues();
        validateBase(configuration, inputs, issues);
        SparkCanvasTable source = inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "轨迹观测表不在上游数据中："
                    + configuration.sourceTableName(), "configuration.sourceTableName");
        }
        SparkCanvasTable interestTable = configuration.interestSource()
                == TraceProximityInterestSource.TABLE
                ? inputs.get(configuration.entitiesOfInterestTableName()) : null;
        if (configuration.interestSource() == TraceProximityInterestSource.TABLE
                && !CanvasNodeSupport.blank(configuration.entitiesOfInterestTableName())
                && interestTable == null) {
            issues.error("TABLE_NOT_FOUND", "起始实体表不在上游数据中："
                    + configuration.entitiesOfInterestTableName(),
                    "configuration.entitiesOfInterestTableName");
        }
        if (source == null || configuration.interestSource() == TraceProximityInterestSource.TABLE
                && interestTable == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        GeometryTypeDefinition geometry = validateSchemas(
                configuration, source, interestTable, issues);
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);

        List<CanvasColumnSchema> eventColumns = eventColumns(source.schema(), configuration);
        List<CanvasColumnSchema> trackColumns = trackColumns(source.schema(), configuration);
        Dataset<Row> events;
        Dataset<Row> tracks = null;
        if (context.runtimeValues().preview()) {
            TraceProximityEventsSupport.Result result = TraceProximityEventsSupport.schemaPlan(
                    source.dataset(), interestTable == null ? null : interestTable.dataset(),
                    configuration);
            events = result.events();
            tracks = result.tracks();
        } else {
            if (!ensureCheckpointDirectory(source.dataset(), issues)) {
                return CanvasNodeOperationResult.invalid(inputSchemas);
            }
            TraceProximityEventsSupport.Result result = TraceProximityEventsSupport.run(
                    source.dataset(), interestTable == null ? null : interestTable.dataset(),
                    configuration, geometry);
            events = result.events();
            tracks = result.tracks();
        }

        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        CanvasTableSchema eventSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, eventColumns,
                CanvasDatasetKind.BOUNDED, null, null);
        output.put(eventSchema.name(), new SparkCanvasTable(eventSchema, events));
        if (configuration.includeTracks()) {
            CanvasTableSchema tracksSchema = new CanvasTableSchema(
                    configuration.tracksOutputTableName(), null, trackColumns,
                    CanvasDatasetKind.BOUNDED, null, null);
            output.put(tracksSchema.name(), new SparkCanvasTable(tracksSchema, tracks));
        }
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateBase(
            TraceProximityEventsConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择轨迹观测表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.pointGeometryColumnName(), "请选择 Point Geometry 字段",
                "configuration.pointGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.entityIdColumnName(), "请选择实体 ID 字段",
                "configuration.entityIdColumnName", issues);
        CanvasNodeSupport.required(configuration.timeColumnName(), "请选择观测时间字段",
                "configuration.timeColumnName", issues);
        if (configuration.distanceMethod() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择距离方法", "configuration.distanceMethod");
        }
        if (configuration.spatialSearchDistance() == null
                || !Double.isFinite(configuration.spatialSearchDistance())
                || configuration.spatialSearchDistance() <= 0) {
            issues.error("INVALID_TRACE_PROXIMITY_SPATIAL_DISTANCE", "空间搜索距离必须是有限正数",
                    "configuration.spatialSearchDistance");
        }
        if (configuration.spatialSearchDistanceUnit() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择空间搜索距离单位",
                    "configuration.spatialSearchDistanceUnit");
        }
        if (configuration.temporalSearchDistance() == null
                || configuration.temporalSearchDistance() < 0) {
            issues.error("INVALID_TRACE_PROXIMITY_TEMPORAL_DISTANCE", "时间搜索距离必须是非负整数",
                    "configuration.temporalSearchDistance");
        }
        if (configuration.temporalSearchDistanceUnit() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择时间搜索距离单位",
                    "configuration.temporalSearchDistanceUnit");
        } else if (configuration.temporalSearchDistance() != null
                && configuration.temporalSearchDistance() >= 0
                && !temporalDistanceSupported(configuration)) {
            issues.error("INVALID_TRACE_PROXIMITY_TEMPORAL_DISTANCE", "时间搜索距离超过可执行范围",
                    "configuration.temporalSearchDistance");
        }
        if (configuration.maxTraceDepth() == null || configuration.maxTraceDepth() < 1
                || configuration.maxTraceDepth() > TraceProximityEventsConfiguration.MAX_TRACE_DEPTH) {
            issues.error("INVALID_TRACE_PROXIMITY_MAX_DEPTH", "最大传播深度必须为 1～"
                    + TraceProximityEventsConfiguration.MAX_TRACE_DEPTH,
                    "configuration.maxTraceDepth");
        }
        validateInterest(configuration, issues);
        validateAttributes(configuration, issues);
        validateOutputNames(configuration, inputs, issues);
    }

    private static void validateInterest(
            TraceProximityEventsConfiguration configuration,
            CanvasNodeIssueSink issues
    ) {
        if (configuration.interestSource() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择起始实体来源", "configuration.interestSource");
            return;
        }
        if (configuration.interestSource() == TraceProximityInterestSource.ENTITY_IDS) {
            List<TraceProximityEntityOfInterest> values = configuration.entitiesOfInterest();
            if (values == null || values.isEmpty()) {
                issues.error("TRACE_PROXIMITY_INTEREST_REQUIRED", "请至少配置一个起始实体",
                        "configuration.entitiesOfInterest");
                return;
            }
            if (values.size() > TraceProximityEventsConfiguration.MAX_ENTITIES_OF_INTEREST) {
                issues.error("TRACE_PROXIMITY_INTEREST_COUNT_EXCEEDED", "起始实体不能超过 "
                        + TraceProximityEventsConfiguration.MAX_ENTITIES_OF_INTEREST + " 项",
                        "configuration.entitiesOfInterest");
            }
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < values.size(); index++) {
                TraceProximityEntityOfInterest value = values.get(index);
                String path = "configuration.entitiesOfInterest[" + index + "]";
                if (value == null || CanvasNodeSupport.blank(value.entityId())) {
                    issues.error("TRACE_PROXIMITY_INTEREST_ID_INVALID", "起始实体 ID 不能为空",
                            path + ".entityId");
                } else if (!seen.add(value.entityId())) {
                    issues.error("DUPLICATE_TRACE_PROXIMITY_INTEREST_ID", "起始实体 ID 不能重复",
                            path + ".entityId");
                }
            }
            return;
        }
        CanvasNodeSupport.required(configuration.entitiesOfInterestTableName(), "请选择起始实体表",
                "configuration.entitiesOfInterestTableName", issues);
        CanvasNodeSupport.required(configuration.interestEntityIdColumnName(), "请选择起始实体 ID 字段",
                "configuration.interestEntityIdColumnName", issues);
        if (configuration.interestStartTimeColumnName() != null
                && configuration.interestStartTimeColumnName().isBlank()) {
            issues.error("REQUIRED_CONFIGURATION", "起始时间字段不能是空字符串",
                    "configuration.interestStartTimeColumnName");
        }
    }

    private static void validateAttributes(
            TraceProximityEventsConfiguration configuration,
            CanvasNodeIssueSink issues
    ) {
        List<String> columns = configuration.attributeMatchColumns();
        if (columns == null) {
            issues.error("INVALID_TRACE_PROXIMITY_ATTRIBUTE_COLUMNS", "属性匹配字段必须是数组",
                    "configuration.attributeMatchColumns");
            return;
        }
        if (columns.size() > TraceProximityEventsConfiguration.MAX_ATTRIBUTE_MATCH_COLUMNS) {
            issues.error("TRACE_PROXIMITY_ATTRIBUTE_COUNT_EXCEEDED", "属性匹配字段不能超过 "
                    + TraceProximityEventsConfiguration.MAX_ATTRIBUTE_MATCH_COLUMNS + " 项",
                    "configuration.attributeMatchColumns");
        }
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < columns.size(); index++) {
            String column = columns.get(index);
            if (CanvasNodeSupport.blank(column)) {
                issues.error("REQUIRED_CONFIGURATION", "属性匹配字段不能为空",
                        "configuration.attributeMatchColumns[" + index + "]");
            } else if (!seen.add(column.toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_TRACE_PROXIMITY_ATTRIBUTE_COLUMN", "属性匹配字段不能重复：" + column,
                        "configuration.attributeMatchColumns[" + index + "]");
            }
        }
    }

    private static void validateOutputNames(
            TraceProximityEventsConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入邻近事件输出表名",
                "configuration.outputTableName", issues);
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }
        if (configuration.includeTracks()) {
            CanvasNodeSupport.required(configuration.tracksOutputTableName(), "请输入后续轨迹输出表名",
                    "configuration.tracksOutputTableName", issues);
            if (!CanvasNodeSupport.blank(configuration.tracksOutputTableName())
                    && inputs.containsKey(configuration.tracksOutputTableName())) {
                issues.error("DUPLICATE_TABLE_NAME", "轨迹输出表名已存在："
                        + configuration.tracksOutputTableName(), "configuration.tracksOutputTableName");
            }
            if (!CanvasNodeSupport.blank(configuration.outputTableName())
                    && configuration.outputTableName().equalsIgnoreCase(
                    configuration.tracksOutputTableName())) {
                issues.error("DUPLICATE_TABLE_NAME", "事件和轨迹输出表名不能相同",
                        "configuration.tracksOutputTableName");
            }
        }
        requiredOutputColumn(configuration.fromEntityIdColumnName(), "上游实体字段",
                "configuration.fromEntityIdColumnName", issues);
        requiredOutputColumn(configuration.toEntityIdColumnName(), "下游实体字段",
                "configuration.toEntityIdColumnName", issues);
        requiredOutputColumn(configuration.depthColumnName(), "传播深度字段",
                "configuration.depthColumnName", issues);
        requiredOutputColumn(configuration.durationMinutesColumnName(), "接触时长字段",
                "configuration.durationMinutesColumnName", issues);
        requiredOutputColumn(configuration.eventTimeColumnName(), "首次接触时间字段",
                "configuration.eventTimeColumnName", issues);
    }

    private static void requiredOutputColumn(
            String value, String label, String path, CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(value, "请输入" + label + "名", path, issues);
    }

    private static GeometryTypeDefinition validateSchemas(
            TraceProximityEventsConfiguration configuration,
            SparkCanvasTable source,
            SparkCanvasTable interestTable,
            CanvasNodeIssueSink issues
    ) {
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "邻近传播追踪只支持有界输入",
                    "configuration.sourceTableName");
        }
        Map<String, CanvasColumnSchema> columns = CanvasNodeSupport.columns(source.schema());
        CanvasColumnSchema geometryColumn = requiredColumn(columns,
                configuration.pointGeometryColumnName(), "Point Geometry",
                "configuration.pointGeometryColumnName", issues);
        GeometryTypeDefinition geometry = geometryColumn == null ? null : geometryColumn.geometry();
        if (geometryColumn != null && (geometryColumn.fieldType() != PlatformDataType.GEOMETRY
                || geometry == null || geometry.crs() == null
                || geometry.dimension() != CoordinateDimension.XY
                || geometry.kind() != GeometryKind.POINT)) {
            issues.error("TRACE_PROXIMITY_POINT_GEOMETRY_REQUIRED",
                    "邻近传播追踪需要带完整 CRS 的 XY Point Geometry",
                    "configuration.pointGeometryColumnName");
            geometry = null;
        }
        CanvasColumnSchema entity = requiredColumn(columns, configuration.entityIdColumnName(),
                "实体 ID", "configuration.entityIdColumnName", issues);
        if (entity != null && entity.fieldType() != PlatformDataType.STRING) {
            issues.error("TRACE_PROXIMITY_ENTITY_ID_STRING_REQUIRED", "实体 ID 字段必须是 STRING",
                    "configuration.entityIdColumnName");
        }
        CanvasColumnSchema time = requiredColumn(columns, configuration.timeColumnName(),
                "观测时间", "configuration.timeColumnName", issues);
        if (time != null && time.fieldType() != PlatformDataType.TIMESTAMP) {
            issues.error("TIMESTAMP_COLUMN_REQUIRED", "观测时间字段必须是 TIMESTAMP",
                    "configuration.timeColumnName");
        }
        for (int index = 0; index < configuration.attributeMatchColumns().size(); index++) {
            String name = configuration.attributeMatchColumns().get(index);
            CanvasColumnSchema column = requiredColumn(columns, name, "属性匹配",
                    "configuration.attributeMatchColumns[" + index + "]", issues);
            if (column != null && column.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", "属性匹配不能使用 Geometry 字段：" + name,
                        "configuration.attributeMatchColumns[" + index + "]");
            }
        }
        validateDistance(configuration, geometry, issues);
        validateAppendedNames(configuration, columns, issues);
        if (interestTable != null) validateInterestSchema(configuration, interestTable, issues);
        return geometry;
    }

    private static void validateDistance(
            TraceProximityEventsConfiguration configuration,
            GeometryTypeDefinition geometry,
            CanvasNodeIssueSink issues
    ) {
        if (geometry == null || configuration.distanceMethod() == null
                || configuration.spatialSearchDistance() == null
                || !Double.isFinite(configuration.spatialSearchDistance())
                || configuration.spatialSearchDistance() <= 0
                || configuration.spatialSearchDistanceUnit() == null) return;
        if (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            if (!("EPSG".equals(geometry.crs().authority()) && geometry.crs().code() == 4326)) {
                issues.error("GEODESIC_DISTANCE_REQUIRES_WGS84",
                        "测地邻近追踪仅支持 EPSG:4326 XY，请先显式空间转换",
                        "configuration.distanceMethod");
            }
            if (configuration.spatialSearchDistanceUnit() == SpatialDistanceUnit.SOURCE_CRS_UNIT) {
                issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED", "测地距离不能使用来源 CRS 单位",
                        "configuration.spatialSearchDistanceUnit");
            }
            return;
        }
        SpatialDistanceSupport.Resolution resolution = SpatialDistanceSupport.resolve(
                configuration.spatialSearchDistance(), configuration.spatialSearchDistanceUnit(), geometry.crs());
        if (!resolution.valid()) {
            issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED", resolution.error(),
                    "configuration.spatialSearchDistanceUnit");
        } else if (resolution.angular()) {
            issues.error("TRACE_PROXIMITY_PROJECTED_CRS_REQUIRED",
                    "平面邻近追踪必须使用投影 CRS，请先显式空间转换",
                    "configuration.pointGeometryColumnName");
        }
    }

    private static void validateAppendedNames(
            TraceProximityEventsConfiguration configuration,
            Map<String, CanvasColumnSchema> sourceColumns,
            CanvasNodeIssueSink issues
    ) {
        String[] paths = {
                "configuration.fromEntityIdColumnName",
                "configuration.toEntityIdColumnName",
                "configuration.depthColumnName",
                "configuration.durationMinutesColumnName",
                "configuration.eventTimeColumnName"
        };
        String[] values = {
                configuration.fromEntityIdColumnName(),
                configuration.toEntityIdColumnName(),
                configuration.depthColumnName(),
                configuration.durationMinutesColumnName(),
                configuration.eventTimeColumnName()
        };
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < values.length; index++) {
            String value = values[index];
            if (CanvasNodeSupport.blank(value)) continue;
            String normalized = value.toLowerCase(Locale.ROOT);
            if (!seen.add(normalized) || sourceColumns.keySet().stream()
                    .anyMatch(name -> name.equalsIgnoreCase(value))) {
                issues.error("DUPLICATE_COLUMN_NAME", "输出字段名重复或已存在：" + value,
                        paths[index]);
            }
        }
    }

    private static boolean temporalDistanceSupported(
            TraceProximityEventsConfiguration configuration
    ) {
        long value = configuration.temporalSearchDistance();
        try {
            return switch (configuration.temporalSearchDistanceUnit()) {
                case MILLISECONDS -> multiplyFits(value, 1_000L);
                case SECONDS -> multiplyFits(value, 1_000_000L);
                case MINUTES -> multiplyFits(value, 60_000_000L);
                case HOURS -> multiplyFits(value, 3_600_000_000L);
                case DAYS -> multiplyFits(value, 86_400_000_000L);
                case WEEKS -> multiplyFits(value, 604_800_000_000L);
                case MONTHS -> value <= Integer.MAX_VALUE;
                case YEARS -> value <= Integer.MAX_VALUE / 12L;
            };
        } catch (ArithmeticException ignored) {
            return false;
        }
    }

    private static boolean multiplyFits(long value, long factor) {
        Math.multiplyExact(value, factor);
        return true;
    }

    private static void validateInterestSchema(
            TraceProximityEventsConfiguration configuration,
            SparkCanvasTable interestTable,
            CanvasNodeIssueSink issues
    ) {
        if (interestTable.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "起始实体表必须是有界表",
                    "configuration.entitiesOfInterestTableName");
        }
        Map<String, CanvasColumnSchema> columns = CanvasNodeSupport.columns(interestTable.schema());
        CanvasColumnSchema id = requiredColumn(columns, configuration.interestEntityIdColumnName(),
                "起始实体 ID", "configuration.interestEntityIdColumnName", issues);
        if (id != null && id.fieldType() != PlatformDataType.STRING) {
            issues.error("TRACE_PROXIMITY_ENTITY_ID_STRING_REQUIRED", "起始实体 ID 字段必须是 STRING",
                    "configuration.interestEntityIdColumnName");
        }
        if (!CanvasNodeSupport.blank(configuration.interestStartTimeColumnName())) {
            CanvasColumnSchema time = requiredColumn(columns, configuration.interestStartTimeColumnName(),
                    "起始时间", "configuration.interestStartTimeColumnName", issues);
            if (time != null && time.fieldType() != PlatformDataType.TIMESTAMP) {
                issues.error("TIMESTAMP_COLUMN_REQUIRED", "起始时间字段必须是 TIMESTAMP",
                        "configuration.interestStartTimeColumnName");
            }
        }
    }

    private static CanvasColumnSchema requiredColumn(
            Map<String, CanvasColumnSchema> columns,
            String name,
            String label,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(name)) return null;
        CanvasColumnSchema column = columns.get(name);
        if (column == null) issues.error("COLUMN_NOT_FOUND", label + "字段不存在：" + name, path);
        return column;
    }

    private static List<CanvasColumnSchema> eventColumns(
            CanvasTableSchema source,
            TraceProximityEventsConfiguration configuration
    ) {
        List<CanvasColumnSchema> columns = new ArrayList<>(source.columns());
        CanvasColumnSchema entity = CanvasNodeSupport.columns(source).get(configuration.entityIdColumnName());
        columns.add(TrackNodeSupport.renamed(entity, configuration.fromEntityIdColumnName(), false));
        columns.add(TrackNodeSupport.renamed(entity, configuration.toEntityIdColumnName(), false));
        columns.add(TrackNodeSupport.longColumn(configuration.depthColumnName(), false));
        columns.add(TrackNodeSupport.doubleColumn(configuration.durationMinutesColumnName(), false));
        columns.add(TrackNodeSupport.timestampColumn(configuration.eventTimeColumnName(), false));
        return columns;
    }

    private static List<CanvasColumnSchema> trackColumns(
            CanvasTableSchema source,
            TraceProximityEventsConfiguration configuration
    ) {
        List<CanvasColumnSchema> columns = new ArrayList<>(source.columns());
        columns.add(TrackNodeSupport.longColumn(configuration.depthColumnName(), false));
        return columns;
    }

    static boolean ensureCheckpointDirectory(Dataset<Row> source, CanvasNodeIssueSink issues) {
        var sparkContext = source.sparkSession().sparkContext();
        if (sparkContext.getCheckpointDir().nonEmpty()) return true;
        String configured = sparkContext.getConf().contains("spark.checkpoint.dir")
                ? sparkContext.getConf().get("spark.checkpoint.dir") : null;
        if (!CanvasNodeSupport.blank(configured)) {
            sparkContext.setCheckpointDir(configured.trim());
            return true;
        }
        if (sparkContext.master().startsWith("local")) {
            String applicationId = sparkContext.applicationId().replaceAll("[^A-Za-z0-9._-]", "_");
            sparkContext.setCheckpointDir(Path.of(System.getProperty("java.io.tmpdir"),
                    "datascalpel-spark-checkpoints", applicationId).toUri().toString());
            return true;
        }
        issues.error("TRACE_PROXIMITY_CHECKPOINT_NOT_CONFIGURED",
                "集群执行邻近传播追踪前必须配置所有执行器可访问的 spark.checkpoint.dir",
                "configuration.maxTraceDepth");
        return false;
    }
}

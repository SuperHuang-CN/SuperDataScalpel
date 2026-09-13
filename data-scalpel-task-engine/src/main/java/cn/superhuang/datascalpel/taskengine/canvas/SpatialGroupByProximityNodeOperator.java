package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityAttributeCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityAttributeRelationship;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityConfiguration;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximitySpatialRelationship;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityTemporalCondition;
import cn.superhuang.data.scalpel.contract.task.SpatialGroupByProximityTemporalUnit;
import cn.superhuang.data.scalpel.contract.task.SpatialJoinSpatialNearCondition;
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

/** ArcGIS-style undirected proximity grouping with transitive connected-component semantics. */
public final class SpatialGroupByProximityNodeOperator implements CanvasNodeOperator {

    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SPATIAL_GROUP_BY_PROXIMITY;
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
        if (!(definition instanceof SpatialGroupByProximityNodeDefinition node)) {
            throw new IllegalArgumentException("SPATIAL_GROUP_BY_PROXIMITY operator received "
                    + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SpatialGroupByProximityConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        CanvasNodeIssueSink issues = context.issues();
        validateBase(configuration, inputs, issues);
        SparkCanvasTable source = inputs.get(configuration.sourceTableName());
        if (!CanvasNodeSupport.blank(configuration.sourceTableName()) && source == null) {
            issues.error("TABLE_NOT_FOUND", "来源表不在上游数据中：" + configuration.sourceTableName(),
                    "configuration.sourceTableName");
        }
        if (source == null) return CanvasNodeOperationResult.invalid(inputSchemas);

        Validation validation = validateSchema(configuration, source, issues);
        if (issues.hasErrors()) return CanvasNodeOperationResult.invalid(inputSchemas);

        Dataset<Row> grouped;
        if (context.runtimeValues().preview()) {
            grouped = SpatialGroupByProximitySupport.schemaPlan(
                    source.dataset(), configuration);
        } else {
            if (!ensureCheckpointDirectory(source.dataset(), issues)) {
                return CanvasNodeOperationResult.invalid(inputSchemas);
            }
            grouped = SpatialGroupByProximitySupport.run(
                    source.dataset(), configuration, validation.geometry());
        }

        List<CanvasColumnSchema> outputColumns = new ArrayList<>(source.schema().columns());
        outputColumns.add(TrackNodeSupport.longColumn(configuration.groupIdColumnName(), false));
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns,
                CanvasDatasetKind.BOUNDED, null, null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, grouped));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateBase(
            SpatialGroupByProximityConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.sourceTableName(), "请选择来源表",
                "configuration.sourceTableName", issues);
        CanvasNodeSupport.required(configuration.geometryColumnName(), "请选择 Geometry 字段",
                "configuration.geometryColumnName", issues);
        if (configuration.spatialRelationship() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择空间关系",
                    "configuration.spatialRelationship");
        }
        CanvasNodeSupport.required(configuration.groupIdColumnName(), "请输入分组 ID 字段名",
                "configuration.groupIdColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入输出表名",
                "configuration.outputTableName", issues);
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }
        if (configuration.attributeConditions() == null) {
            issues.error("INVALID_SPATIAL_GROUP_ATTRIBUTE_CONDITIONS", "属性关系必须是数组",
                    "configuration.attributeConditions");
        } else if (configuration.attributeConditions().size()
                > SpatialGroupByProximityConfiguration.MAX_ATTRIBUTE_CONDITIONS) {
            issues.error("SPATIAL_GROUP_ATTRIBUTE_CONDITION_COUNT_EXCEEDED",
                    "属性关系不能超过 " + SpatialGroupByProximityConfiguration.MAX_ATTRIBUTE_CONDITIONS + " 项",
                    "configuration.attributeConditions");
        }
    }

    private static Validation validateSchema(
            SpatialGroupByProximityConfiguration configuration,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues
    ) {
        if (source.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "邻近分组只支持有界输入",
                    "configuration.sourceTableName");
        }
        Map<String, CanvasColumnSchema> columns = CanvasNodeSupport.columns(source.schema());
        CanvasColumnSchema geometry = column(
                columns, configuration.geometryColumnName(), "Geometry",
                "configuration.geometryColumnName", issues);
        GeometryTypeDefinition geometryType = geometry == null ? null : geometry.geometry();
        if (geometry != null && (geometry.fieldType() != PlatformDataType.GEOMETRY
                || geometryType == null || geometryType.crs() == null
                || geometryType.dimension() != CoordinateDimension.XY
                || !supportedGeometry(geometryType.kind()))) {
            issues.error("SPATIAL_GROUP_GEOMETRY_UNSUPPORTED",
                    "邻近分组需要带完整 CRS 的 XY Point、Line 或 Polygon Geometry",
                    "configuration.geometryColumnName");
            geometryType = null;
        }
        if (geometryType != null
                && configuration.spatialRelationship()
                == SpatialGroupByProximitySpatialRelationship.TOUCHES
                && pointFamily(geometryType.kind())) {
            issues.error("SPATIAL_GROUP_TOUCHES_GEOMETRY_UNSUPPORTED",
                    "Touches 仅支持 Line 或 Polygon 要素",
                    "configuration.spatialRelationship");
        }
        validateSpatialDistance(configuration, geometryType, issues);
        validateTemporal(configuration.temporalCondition(), columns, issues);
        validateAttributes(configuration.attributeConditions(), columns, issues);
        if (!CanvasNodeSupport.blank(configuration.groupIdColumnName())
                && columns.keySet().stream().anyMatch(name -> name.equalsIgnoreCase(
                configuration.groupIdColumnName()))) {
            issues.error("DUPLICATE_COLUMN_NAME", "分组 ID 字段与来源字段重名："
                    + configuration.groupIdColumnName(), "configuration.groupIdColumnName");
        }
        return new Validation(geometryType);
    }

    private static void validateSpatialDistance(
            SpatialGroupByProximityConfiguration configuration,
            GeometryTypeDefinition geometry,
            CanvasNodeIssueSink issues
    ) {
        SpatialGroupByProximitySpatialRelationship relationship = configuration.spatialRelationship();
        if (relationship == null || !relationship.usesDistance()) return;
        if (configuration.spatialNearDistance() == null
                || !Double.isFinite(configuration.spatialNearDistance())
                || configuration.spatialNearDistance() <= 0) {
            issues.error("INVALID_SPATIAL_GROUP_NEAR_DISTANCE",
                    "空间邻近距离必须是有限正数", "configuration.spatialNearDistance");
        }
        if (configuration.spatialNearDistanceUnit() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择空间邻近距离单位",
                    "configuration.spatialNearDistanceUnit");
        }
        if (geometry == null || configuration.spatialNearDistance() == null
                || !Double.isFinite(configuration.spatialNearDistance())
                || configuration.spatialNearDistance() <= 0
                || configuration.spatialNearDistanceUnit() == null) return;

        if (relationship == SpatialGroupByProximitySpatialRelationship.NEAR_GEODESIC) {
            if (!wgs84(geometry)) {
                issues.error("GEODESIC_DISTANCE_REQUIRES_WGS84",
                        "Near Geodesic 仅支持 EPSG:4326 XY，请先显式空间转换",
                        "configuration.spatialRelationship");
            }
            if (configuration.spatialNearDistanceUnit() == SpatialDistanceUnit.SOURCE_CRS_UNIT) {
                issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED",
                        "Near Geodesic 不能使用来源 CRS 单位",
                        "configuration.spatialNearDistanceUnit");
            } else if (!Double.isFinite(configuration.spatialNearDistance()
                    * SpatialDistanceSupport.metresPerConfiguredUnit(
                    configuration.spatialNearDistanceUnit()))) {
                issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED",
                        "空间邻近距离换算后不是有限数值",
                        "configuration.spatialNearDistanceUnit");
            }
            return;
        }
        SpatialDistanceSupport.Resolution resolved = SpatialDistanceSupport.resolve(
                configuration.spatialNearDistance(), configuration.spatialNearDistanceUnit(),
                geometry.crs());
        if (!resolved.valid()) {
            issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED", resolved.error(),
                    "configuration.spatialNearDistanceUnit");
        } else if (resolved.angular()) {
            issues.error("SPATIAL_GROUP_PROJECTED_CRS_REQUIRED",
                    "Near Planar 必须使用投影 CRS，请先显式空间转换",
                    "configuration.geometryColumnName");
        }
    }

    private static void validateTemporal(
            SpatialGroupByProximityTemporalCondition condition,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (condition == null) return;
        String path = "configuration.temporalCondition";
        if (condition.relationship() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择时间关系", path + ".relationship");
        }
        CanvasNodeSupport.required(condition.startColumnName(), "请选择开始或瞬时时间字段",
                path + ".startColumnName", issues);
        CanvasColumnSchema start = column(columns, condition.startColumnName(), "开始或瞬时时间",
                path + ".startColumnName", issues);
        CanvasColumnSchema end = null;
        if (condition.endColumnName() != null) {
            if (condition.endColumnName().isBlank()) {
                issues.error("REQUIRED_CONFIGURATION", "结束时间字段不能为空字符串",
                        path + ".endColumnName");
            } else {
                end = column(columns, condition.endColumnName(), "结束时间",
                        path + ".endColumnName", issues);
            }
        }
        if (start != null && !temporal(start.fieldType())) {
            issues.error("TEMPORAL_COLUMN_REQUIRED",
                    "开始或瞬时时间字段必须是 DATE、TIMESTAMP 或 TIMESTAMP_NTZ",
                    path + ".startColumnName");
        }
        if (end != null && !temporal(end.fieldType())) {
            issues.error("TEMPORAL_COLUMN_REQUIRED",
                    "结束时间字段必须是 DATE、TIMESTAMP 或 TIMESTAMP_NTZ",
                    path + ".endColumnName");
        }
        if (start != null && end != null && temporal(start.fieldType())
                && temporal(end.fieldType()) && start.fieldType() != end.fieldType()) {
            issues.error("SPATIAL_GROUP_TEMPORAL_TYPE_MISMATCH",
                    "开始和结束时间字段必须使用相同类型", path + ".endColumnName");
        }
        if (condition.relationship() != null && condition.relationship().usesDistance()) {
            if (condition.nearDistance() == null || condition.nearDistance() <= 0) {
                issues.error("INVALID_SPATIAL_GROUP_TEMPORAL_NEAR_DISTANCE",
                        "时间邻近距离必须是正整数", path + ".nearDistance");
            }
            if (condition.nearDistanceUnit() == null) {
                issues.error("REQUIRED_CONFIGURATION", "请选择时间邻近距离单位",
                        path + ".nearDistanceUnit");
            }
            validateTemporalRange(condition, issues, path);
        }
    }

    private static void validateTemporalRange(
            SpatialGroupByProximityTemporalCondition condition,
            CanvasNodeIssueSink issues,
            String path
    ) {
        if (condition.nearDistance() == null || condition.nearDistance() <= 0
                || condition.nearDistanceUnit() == null) return;
        try {
            switch (condition.nearDistanceUnit()) {
                case MILLISECONDS -> Math.multiplyExact(condition.nearDistance(), 1_000L);
                case SECONDS -> Math.multiplyExact(condition.nearDistance(), 1_000_000L);
                case MINUTES -> Math.multiplyExact(condition.nearDistance(), 60_000_000L);
                case HOURS -> Math.multiplyExact(condition.nearDistance(), 3_600_000_000L);
                case DAYS -> Math.multiplyExact(condition.nearDistance(), 86_400_000_000L);
                case WEEKS -> Math.multiplyExact(condition.nearDistance(), 604_800_000_000L);
                case MONTHS -> Math.multiplyExact(condition.nearDistance(), 1L);
                case YEARS -> Math.multiplyExact(condition.nearDistance(), 12L);
            }
        } catch (ArithmeticException ignored) {
            issues.error("SPATIAL_GROUP_TEMPORAL_NEAR_DISTANCE_OVERFLOW",
                    "时间邻近距离超出可执行范围", path + ".nearDistance");
        }
    }

    private static void validateAttributes(
            List<SpatialGroupByProximityAttributeCondition> conditions,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (conditions == null) return;
        Set<String> sources = new HashSet<>();
        for (int index = 0; index < conditions.size(); index++) {
            SpatialGroupByProximityAttributeCondition condition = conditions.get(index);
            String path = "configuration.attributeConditions[" + index + "]";
            if (condition == null) {
                issues.error("INVALID_SPATIAL_GROUP_ATTRIBUTE_CONDITION",
                        "属性关系配置不能为空", path);
                continue;
            }
            CanvasNodeSupport.required(condition.columnName(), "请选择属性字段",
                    path + ".columnName", issues);
            if (condition.relationship() == null) {
                issues.error("REQUIRED_CONFIGURATION", "请选择属性关系",
                        path + ".relationship");
            }
            if (!CanvasNodeSupport.blank(condition.columnName())
                    && !sources.add(condition.columnName().toLowerCase(Locale.ROOT))) {
                issues.error("DUPLICATE_SPATIAL_GROUP_ATTRIBUTE_COLUMN",
                        "属性字段重复：" + condition.columnName(), path + ".columnName");
            }
            CanvasColumnSchema column = column(columns, condition.columnName(), "属性",
                    path + ".columnName", issues);
            if (column != null && column.fieldType() == PlatformDataType.GEOMETRY) {
                issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                        "Geometry 不能作为属性关系字段", path + ".columnName");
            }
            if (condition.relationship()
                    == SpatialGroupByProximityAttributeRelationship.ABSOLUTE_DIFFERENCE_AT_MOST) {
                if (column != null && !numeric(column.fieldType())) {
                    issues.error("NUMERIC_COLUMN_REQUIRED",
                            "绝对差关系需要数值字段", path + ".columnName");
                }
                if (condition.maximumDifference() == null
                        || !Double.isFinite(condition.maximumDifference())
                        || condition.maximumDifference() < 0) {
                    issues.error("INVALID_SPATIAL_GROUP_ATTRIBUTE_DIFFERENCE",
                            "属性绝对差阈值必须是有限非负数", path + ".maximumDifference");
                }
            }
        }
    }

    private static CanvasColumnSchema column(
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

    private static boolean supportedGeometry(GeometryKind kind) {
        return pointFamily(kind) || kind == GeometryKind.LINESTRING
                || kind == GeometryKind.MULTILINESTRING || kind == GeometryKind.POLYGON
                || kind == GeometryKind.MULTIPOLYGON;
    }

    private static boolean pointFamily(GeometryKind kind) {
        return kind == GeometryKind.POINT || kind == GeometryKind.MULTIPOINT;
    }

    private static boolean wgs84(GeometryTypeDefinition geometry) {
        return "EPSG".equalsIgnoreCase(geometry.crs().authority())
                && geometry.crs().code() == 4326
                && geometry.dimension() == CoordinateDimension.XY;
    }

    private static boolean temporal(PlatformDataType type) {
        return type == PlatformDataType.DATE || type == PlatformDataType.TIMESTAMP
                || type == PlatformDataType.TIMESTAMP_NTZ;
    }

    private static boolean numeric(PlatformDataType type) {
        return switch (type) {
            case BYTE, SHORT, INTEGER, LONG, FLOAT, DOUBLE, DECIMAL -> true;
            default -> false;
        };
    }

    private static boolean ensureCheckpointDirectory(
            Dataset<Row> source,
            CanvasNodeIssueSink issues
    ) {
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
            sparkContext.setCheckpointDir(Path.of(
                    System.getProperty("java.io.tmpdir"),
                    "datascalpel-spark-checkpoints", applicationId).toUri().toString());
            return true;
        }
        issues.error("SPATIAL_GROUP_CHECKPOINT_NOT_CONFIGURED",
                "集群模式执行邻近连通分组前必须配置 spark.checkpoint.dir，并指向所有执行器可访问的文件系统",
                "configuration.spatialRelationship");
        return false;
    }

    static SpatialJoinSpatialNearCondition nearCondition(
            SpatialGroupByProximityConfiguration configuration
    ) {
        SpatialDistanceMethod method = configuration.spatialRelationship()
                == SpatialGroupByProximitySpatialRelationship.NEAR_GEODESIC
                ? SpatialDistanceMethod.GEODESIC : SpatialDistanceMethod.PLANAR;
        return new SpatialJoinSpatialNearCondition(
                configuration.geometryColumnName(), configuration.geometryColumnName(), method,
                configuration.spatialNearDistance(), configuration.spatialNearDistanceUnit());
    }

    private record Validation(GeometryTypeDefinition geometry) {
    }
}

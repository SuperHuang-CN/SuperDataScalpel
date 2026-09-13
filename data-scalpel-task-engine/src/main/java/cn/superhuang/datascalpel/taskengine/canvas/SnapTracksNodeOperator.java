package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.CanvasDatasetKind;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CanvasTableSchema;
import cn.superhuang.data.scalpel.contract.task.SnapTracksConfiguration;
import cn.superhuang.data.scalpel.contract.task.SnapTracksDirectionMatching;
import cn.superhuang.data.scalpel.contract.task.SnapTracksLineField;
import cn.superhuang.data.scalpel.contract.task.SnapTracksNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.SnapTracksOutputMode;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceMethod;
import cn.superhuang.data.scalpel.contract.task.SpatialDistanceUnit;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import cn.superhuang.datascalpel.taskengine.spark.SparkTypeMapper;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Topology- and direction-aware bounded track map matching. */
public final class SnapTracksNodeOperator implements CanvasNodeOperator {
    @Override
    public CanvasNodeType nodeType() {
        return CanvasNodeType.SNAP_TRACKS;
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
        if (!(definition instanceof SnapTracksNodeDefinition node)) {
            throw new IllegalArgumentException("SNAP_TRACKS operator received " + definition.nodeType());
        }
        List<CanvasTableSchema> inputSchemas = CanvasNodeSupport.schemas(inputs);
        SnapTracksConfiguration configuration = node.configuration();
        if (configuration == null) return CanvasNodeOperationResult.invalid(inputSchemas);
        CanvasNodeIssueSink issues = context.issues();
        validateBase(configuration, inputs, issues);
        SparkCanvasTable pointTable = inputs.get(configuration.pointTableName());
        SparkCanvasTable lineTable = inputs.get(configuration.lineTableName());
        if (!CanvasNodeSupport.blank(configuration.pointTableName()) && pointTable == null) {
            issues.error("TABLE_NOT_FOUND", "轨迹点表不在上游数据中：" + configuration.pointTableName(),
                    "configuration.pointTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.lineTableName()) && lineTable == null) {
            issues.error("TABLE_NOT_FOUND", "网络线表不在上游数据中：" + configuration.lineTableName(),
                    "configuration.lineTableName");
        }
        if (pointTable == null || lineTable == null) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        TrackNodeSupport.PreparedTrack prepared = TrackNodeSupport.prepare(
                pointTable, configuration.pointGeometryColumnName(), true,
                configuration.trackIdColumns(), configuration.timeColumnName(),
                configuration.distanceMethod(), configuration.boundaries(), issues, "configuration",
                configuration.orderByColumns(), "configuration.orderByColumns");
        GeometryTypeDefinition geometry = validateSchemas(configuration, pointTable, lineTable, issues);
        DistanceResolution distance = resolveDistance(configuration, geometry, issues);
        List<CanvasColumnSchema> outputColumns = outputColumns(
                configuration, pointTable.schema(), lineTable.schema(), geometry, issues);
        if (issues.hasErrors() || prepared == null || geometry == null || !distance.valid()) {
            return CanvasNodeOperationResult.invalid(inputSchemas);
        }

        Dataset<Row> result = context.runtimeValues().preview()
                ? SnapTracksSupport.schemaPlan(
                        pointTable.dataset(), lineTable.dataset(), configuration)
                : SnapTracksSupport.run(prepared, lineTable.dataset(), configuration, geometry,
                        distance.searchDistanceMetres(), distance.sourceUnitsPerMetre());
        result.schema();
        outputColumns = SparkTypeMapper.fromStructType(result.schema(), outputColumns);
        CanvasTableSchema outputSchema = new CanvasTableSchema(
                configuration.outputTableName(), null, outputColumns, CanvasDatasetKind.BOUNDED,
                pointTable.schema().eventTimeColumn(), null);
        Map<String, SparkCanvasTable> output = new LinkedHashMap<>(inputs);
        output.put(outputSchema.name(), new SparkCanvasTable(outputSchema, result));
        return CanvasNodeOperationResult.propagated(output, CanvasNodeSupport.schemas(output));
    }

    private static void validateBase(
            SnapTracksConfiguration configuration,
            Map<String, SparkCanvasTable> inputs,
            CanvasNodeIssueSink issues
    ) {
        CanvasNodeSupport.required(configuration.pointTableName(), "请选择轨迹点表",
                "configuration.pointTableName", issues);
        CanvasNodeSupport.required(configuration.pointGeometryColumnName(), "请选择 Point Geometry 字段",
                "configuration.pointGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.timeColumnName(), "请选择观测时间字段",
                "configuration.timeColumnName", issues);
        CanvasNodeSupport.required(configuration.lineTableName(), "请选择网络线表",
                "configuration.lineTableName", issues);
        CanvasNodeSupport.required(configuration.lineGeometryColumnName(), "请选择网络 LineString 字段",
                "configuration.lineGeometryColumnName", issues);
        CanvasNodeSupport.required(configuration.lineIdColumnName(), "请选择网络线唯一 ID 字段",
                "configuration.lineIdColumnName", issues);
        CanvasNodeSupport.required(configuration.fromNodeColumnName(), "请选择网络线起点字段",
                "configuration.fromNodeColumnName", issues);
        CanvasNodeSupport.required(configuration.toNodeColumnName(), "请选择网络线终点字段",
                "configuration.toNodeColumnName", issues);
        CanvasNodeSupport.required(configuration.outputTableName(), "请输入吸附结果表名",
                "configuration.outputTableName", issues);
        if (!CanvasNodeSupport.blank(configuration.outputTableName())
                && inputs.containsKey(configuration.outputTableName())) {
            issues.error("DUPLICATE_TABLE_NAME", "输出表名已存在：" + configuration.outputTableName(),
                    "configuration.outputTableName");
        }
        if (!CanvasNodeSupport.blank(configuration.pointTableName())
                && configuration.pointTableName().equals(configuration.lineTableName())) {
            issues.error("SNAP_TRACKS_INPUT_TABLES_MUST_DIFFER", "轨迹点表和网络线表必须不同",
                    "configuration.lineTableName");
        }
        if (configuration.distanceMethod() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择距离方法", "configuration.distanceMethod");
        }
        if (configuration.searchDistance() == null || !Double.isFinite(configuration.searchDistance())
                || configuration.searchDistance() <= 0d) {
            issues.error("INVALID_SNAP_TRACKS_SEARCH_DISTANCE", "搜索距离必须是有限正数",
                    "configuration.searchDistance");
        }
        if (configuration.searchDistanceUnit() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择搜索距离单位",
                    "configuration.searchDistanceUnit");
        }
        if (configuration.outputMode() == null) {
            issues.error("REQUIRED_CONFIGURATION", "请选择输出范围", "configuration.outputMode");
        }
        if (configuration.trackIdColumns() == null) {
            issues.error("INVALID_TRACK_ID_COLUMNS", "轨迹标识字段必须是数组",
                    "configuration.trackIdColumns");
        } else if (configuration.trackIdColumns().size() > SnapTracksConfiguration.MAX_TRACK_ID_COLUMNS) {
            issues.error("TRACK_ID_COLUMN_COUNT_EXCEEDED", "轨迹标识字段不能超过 "
                    + SnapTracksConfiguration.MAX_TRACK_ID_COLUMNS + " 项", "configuration.trackIdColumns");
        }
        if (configuration.orderByColumns() == null) {
            issues.error("INVALID_TRACK_ORDER_COLUMNS", "同时间顺序字段必须是数组",
                    "configuration.orderByColumns");
        }
        if (configuration.lineFields() == null) {
            issues.error("INVALID_SNAP_TRACKS_LINE_FIELDS", "网络线输出字段必须是数组",
                    "configuration.lineFields");
        } else if (configuration.lineFields().size() > SnapTracksConfiguration.MAX_LINE_FIELDS) {
            issues.error("SNAP_TRACKS_LINE_FIELD_COUNT_EXCEEDED", "网络线输出字段不能超过 "
                    + SnapTracksConfiguration.MAX_LINE_FIELDS + " 项", "configuration.lineFields");
        }
        validateDirectionDraft(configuration.directionMatching(), issues);
    }

    private static GeometryTypeDefinition validateSchemas(
            SnapTracksConfiguration configuration,
            SparkCanvasTable pointTable,
            SparkCanvasTable lineTable,
            CanvasNodeIssueSink issues
    ) {
        if (pointTable.schema().datasetKind() != CanvasDatasetKind.BOUNDED
                || lineTable.schema().datasetKind() != CanvasDatasetKind.BOUNDED) {
            issues.error("BOUNDED_INPUT_REQUIRED", "轨迹路网吸附只支持有界点表和网络线表",
                    "configuration");
        }
        Map<String, CanvasColumnSchema> pointColumns = CanvasNodeSupport.columns(pointTable.schema());
        Map<String, CanvasColumnSchema> lineColumns = CanvasNodeSupport.columns(lineTable.schema());
        CanvasColumnSchema point = pointColumns.get(configuration.pointGeometryColumnName());
        CanvasColumnSchema line = lineColumns.get(configuration.lineGeometryColumnName());
        GeometryTypeDefinition geometry = point == null ? null : point.geometry();
        if (line == null) {
            issues.error("COLUMN_NOT_FOUND", "网络 LineString 字段不存在："
                    + configuration.lineGeometryColumnName(), "configuration.lineGeometryColumnName");
        } else if (line.fieldType() != PlatformDataType.GEOMETRY || line.geometry() == null
                || line.geometry().kind() != GeometryKind.LINESTRING) {
            issues.error("SNAP_TRACKS_LINE_GEOMETRY_REQUIRED", "网络吸附需要带完整元数据的 LineString",
                    "configuration.lineGeometryColumnName");
        }
        if (geometry != null && line != null && line.geometry() != null) {
            if (!geometry.crs().equals(line.geometry().crs())) {
                issues.error("SPATIAL_CRS_MISMATCH", "轨迹点和网络线 CRS 不一致，请先显式空间转换",
                        "configuration.lineGeometryColumnName");
            }
            if (geometry.dimension() != CoordinateDimension.XY
                    || line.geometry().dimension() != CoordinateDimension.XY) {
                issues.error("UNSUPPORTED_GEOMETRY_DIMENSION", "轨迹路网吸附只支持 XY Point 和 LineString",
                        "configuration.lineGeometryColumnName");
            }
        }
        CanvasColumnSchema lineId = scalar(lineColumns, configuration.lineIdColumnName(), "网络线唯一 ID",
                "configuration.lineIdColumnName", issues);
        CanvasColumnSchema from = scalar(lineColumns, configuration.fromNodeColumnName(), "网络起点",
                "configuration.fromNodeColumnName", issues);
        CanvasColumnSchema to = scalar(lineColumns, configuration.toNodeColumnName(), "网络终点",
                "configuration.toNodeColumnName", issues);
        if (lineId != null && lineId.fieldType() == PlatformDataType.BINARY) {
            issues.error("SNAP_TRACKS_NETWORK_FIELD_TYPE_INVALID", "网络线唯一 ID 不能使用 BINARY",
                    "configuration.lineIdColumnName");
        }
        if (from != null && to != null && from.fieldType() != to.fieldType()) {
            issues.error("SNAP_TRACKS_NODE_TYPE_MISMATCH", "网络起点和终点字段类型必须一致",
                    "configuration.toNodeColumnName");
        }
        if (from != null && from.fieldType() == PlatformDataType.BINARY) {
            issues.error("SNAP_TRACKS_NETWORK_FIELD_TYPE_INVALID", "网络节点不能使用 BINARY",
                    "configuration.fromNodeColumnName");
        }
        validateDirectionSchema(configuration.directionMatching(), lineColumns, issues);
        validateLineFields(configuration, lineColumns, issues);
        return geometry;
    }

    private static CanvasColumnSchema scalar(
            Map<String, CanvasColumnSchema> columns,
            String name,
            String label,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (CanvasNodeSupport.blank(name)) return null;
        CanvasColumnSchema column = columns.get(name);
        if (column == null) {
            issues.error("COLUMN_NOT_FOUND", label + "字段不存在：" + name, path);
            return null;
        }
        if (column.fieldType() == PlatformDataType.GEOMETRY) {
            issues.error("GEOMETRY_FIELD_OPERATION_UNSUPPORTED", label + "不能使用 Geometry", path);
        }
        return column;
    }

    private static void validateDirectionDraft(
            SnapTracksDirectionMatching direction,
            CanvasNodeIssueSink issues
    ) {
        if (direction == null) return;
        CanvasNodeSupport.required(direction.directionColumnName(), "请选择方向字段",
                "configuration.directionMatching.directionColumnName", issues);
        String[] values = {direction.forwardValue(), direction.backwardValue(),
                direction.bothValue(), direction.noneValue()};
        String[] paths = {"forwardValue", "backwardValue", "bothValue", "noneValue"};
        Set<String> seen = new HashSet<>();
        for (int index = 0; index < values.length; index++) {
            String value = values[index];
            if (value == null) {
                issues.error("REQUIRED_CONFIGURATION", "方向映射值不能为 null",
                        "configuration.directionMatching." + paths[index]);
            } else if (!seen.add(value)) {
                issues.error("DUPLICATE_SNAP_TRACKS_DIRECTION_VALUE", "四种方向映射值必须互不相同",
                        "configuration.directionMatching." + paths[index]);
            }
        }
    }

    private static void validateDirectionSchema(
            SnapTracksDirectionMatching direction,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (direction == null || CanvasNodeSupport.blank(direction.directionColumnName())) return;
        CanvasColumnSchema column = scalar(columns, direction.directionColumnName(), "网络方向",
                "configuration.directionMatching.directionColumnName", issues);
        if (column != null && column.fieldType() == PlatformDataType.BINARY) {
            issues.error("SNAP_TRACKS_NETWORK_FIELD_TYPE_INVALID", "网络方向不能使用 BINARY",
                    "configuration.directionMatching.directionColumnName");
        }
    }

    private static void validateLineFields(
            SnapTracksConfiguration configuration,
            Map<String, CanvasColumnSchema> columns,
            CanvasNodeIssueSink issues
    ) {
        if (configuration.lineFields() == null) return;
        Set<String> sources = new HashSet<>();
        Set<String> connectivity = Set.of(
                lower(configuration.lineGeometryColumnName()), lower(configuration.lineIdColumnName()),
                lower(configuration.fromNodeColumnName()), lower(configuration.toNodeColumnName()));
        for (int index = 0; index < configuration.lineFields().size(); index++) {
            SnapTracksLineField field = configuration.lineFields().get(index);
            String path = "configuration.lineFields[" + index + "]";
            if (field == null) {
                issues.error("REQUIRED_CONFIGURATION", "网络线输出字段配置不能为空", path);
                continue;
            }
            CanvasNodeSupport.required(field.sourceColumnName(), "请选择网络线来源字段",
                    path + ".sourceColumnName", issues);
            CanvasNodeSupport.required(field.outputColumnName(), "请输入网络线结果字段名",
                    path + ".outputColumnName", issues);
            if (!CanvasNodeSupport.blank(field.sourceColumnName())) {
                CanvasColumnSchema source = columns.get(field.sourceColumnName());
                if (source == null) {
                    issues.error("COLUMN_NOT_FOUND", "网络线来源字段不存在：" + field.sourceColumnName(),
                            path + ".sourceColumnName");
                } else if (source.fieldType() == PlatformDataType.GEOMETRY
                        || connectivity.contains(lower(field.sourceColumnName()))) {
                    issues.error("SNAP_TRACKS_LINE_FIELD_INVALID", "网络 Geometry 和连接字段不作为附加属性重复输出",
                            path + ".sourceColumnName");
                }
                if (!sources.add(lower(field.sourceColumnName()))) {
                    issues.error("DUPLICATE_COLUMN_NAME", "网络线来源字段不能重复：" + field.sourceColumnName(),
                            path + ".sourceColumnName");
                }
            }
        }
    }

    private static DistanceResolution resolveDistance(
            SnapTracksConfiguration configuration,
            GeometryTypeDefinition geometry,
            CanvasNodeIssueSink issues
    ) {
        if (geometry == null || configuration.distanceMethod() == null
                || configuration.searchDistance() == null
                || !Double.isFinite(configuration.searchDistance())
                || configuration.searchDistance() <= 0d
                || configuration.searchDistanceUnit() == null) {
            return DistanceResolution.invalid();
        }
        if (configuration.distanceMethod() == SpatialDistanceMethod.GEODESIC) {
            if (!("EPSG".equalsIgnoreCase(geometry.crs().authority()) && geometry.crs().code() == 4326
                    && geometry.dimension() == CoordinateDimension.XY)) {
                issues.error("GEODESIC_DISTANCE_REQUIRES_WGS84",
                        "测地路网吸附仅支持 EPSG:4326 XY，请先显式空间转换",
                        "configuration.distanceMethod");
                return DistanceResolution.invalid();
            }
            if (configuration.searchDistanceUnit() == SpatialDistanceUnit.SOURCE_CRS_UNIT) {
                issues.error("SPATIAL_DISTANCE_UNIT_UNSUPPORTED", "测地路网吸附不能使用来源 CRS 单位",
                        "configuration.searchDistanceUnit");
                return DistanceResolution.invalid();
            }
            double metres = configuration.searchDistance()
                    * SpatialDistanceSupport.metresPerConfiguredUnit(configuration.searchDistanceUnit());
            if (!Double.isFinite(metres) || metres <= 0d) {
                issues.error("INVALID_SNAP_TRACKS_SEARCH_DISTANCE", "搜索距离换算后必须是有限正数",
                        "configuration.searchDistance");
                return DistanceResolution.invalid();
            }
            return new DistanceResolution(metres, 1d, true);
        }
        SpatialDistanceSupport.Resolution search = SpatialDistanceSupport.resolve(
                configuration.searchDistance(), configuration.searchDistanceUnit(), geometry.crs());
        SpatialDistanceSupport.Resolution metre = SpatialDistanceSupport.sourceUnitsPerConfiguredUnit(
                SpatialDistanceUnit.METERS, geometry.crs());
        if (!search.valid() || !metre.valid() || search.angular() || metre.angular()) {
            issues.error("SNAP_TRACKS_PROJECTED_CRS_REQUIRED",
                    "平面路网吸附必须使用可换算线性单位的投影 CRS",
                    "configuration.pointGeometryColumnName");
            return DistanceResolution.invalid();
        }
        return new DistanceResolution(search.sourceCrsValue() / metre.sourceCrsValue(),
                metre.sourceCrsValue(), true);
    }

    private static List<CanvasColumnSchema> outputColumns(
            SnapTracksConfiguration configuration,
            CanvasTableSchema pointTable,
            CanvasTableSchema lineTable,
            GeometryTypeDefinition geometry,
            CanvasNodeIssueSink issues
    ) {
        Map<String, CanvasColumnSchema> lineColumns = CanvasNodeSupport.columns(lineTable);
        Set<String> names = new HashSet<>();
        List<CanvasColumnSchema> result = new ArrayList<>(pointTable.columns());
        for (CanvasColumnSchema column : pointTable.columns()) names.add(lower(column.name()));
        if (configuration.lineFields() != null) {
            for (int index = 0; index < configuration.lineFields().size(); index++) {
                SnapTracksLineField field = configuration.lineFields().get(index);
                if (field == null || CanvasNodeSupport.blank(field.sourceColumnName())
                        || CanvasNodeSupport.blank(field.outputColumnName())) continue;
                CanvasColumnSchema source = lineColumns.get(field.sourceColumnName());
                addName(names, field.outputColumnName(), "configuration.lineFields[" + index
                        + "].outputColumnName", issues);
                if (source != null && source.fieldType() != PlatformDataType.GEOMETRY) {
                    result.add(TrackNodeSupport.renamed(source, field.outputColumnName(), true));
                }
            }
        }
        String[] paths = {
                "configuration.matchedLineIdColumnName", "configuration.matchStatusColumnName",
                "configuration.snappedGeometryColumnName", "configuration.originalXColumnName",
                "configuration.originalYColumnName", "configuration.matchXColumnName",
                "configuration.matchYColumnName", "configuration.matchDistanceColumnName"
        };
        String[] values = {
                configuration.matchedLineIdColumnName(), configuration.matchStatusColumnName(),
                configuration.snappedGeometryColumnName(), configuration.originalXColumnName(),
                configuration.originalYColumnName(), configuration.matchXColumnName(),
                configuration.matchYColumnName(), configuration.matchDistanceColumnName()
        };
        for (int index = 0; index < values.length; index++) {
            CanvasNodeSupport.required(values[index], "请输入吸附结果字段名", paths[index], issues);
            if (!CanvasNodeSupport.blank(values[index])) addName(names, values[index], paths[index], issues);
        }
        CanvasColumnSchema lineId = lineColumns.get(configuration.lineIdColumnName());
        if (lineId != null) result.add(TrackNodeSupport.renamed(
                lineId, configuration.matchedLineIdColumnName(), true));
        result.add(new CanvasColumnSchema(configuration.matchStatusColumnName(), PlatformDataType.STRING,
                null, null, null, false, null, false, false, null, null));
        result.add(new CanvasColumnSchema(configuration.snappedGeometryColumnName(), PlatformDataType.GEOMETRY,
                null, null, null, true, null, false, false, null,
                geometry == null ? null : new GeometryTypeDefinition(
                        GeometryKind.POINT, geometry.crs(), CoordinateDimension.XY)));
        for (String name : List.of(configuration.originalXColumnName(), configuration.originalYColumnName(),
                configuration.matchXColumnName(), configuration.matchYColumnName(),
                configuration.matchDistanceColumnName())) {
            result.add(new CanvasColumnSchema(name, PlatformDataType.DOUBLE, null, null, null,
                    true, null, false, false, null, null));
        }
        return result;
    }

    private static void addName(
            Set<String> names,
            String value,
            String path,
            CanvasNodeIssueSink issues
    ) {
        if (!names.add(lower(value))) {
            issues.error("DUPLICATE_COLUMN_NAME", "吸附结果字段名重复：" + value, path);
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private record DistanceResolution(
            double searchDistanceMetres,
            double sourceUnitsPerMetre,
            boolean valid
    ) {
        static DistanceResolution invalid() {
            return new DistanceResolution(Double.NaN, Double.NaN, false);
        }
    }
}

package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Canvas 节点类型稳定编码；同时作为 CanvasNodeDefinition 的 type 判别值，决定节点 configuration 结构、输入输出度数、最低协议次版本以及 BATCH/STREAMING 支持范围。")
public enum CanvasNodeType {
    MODEL_INPUT,
    JDBC_INPUT,
    JDBC_INCREMENTAL_INPUT,
    JDBC_QUERY_INPUT,
    FILE_DATASET_INPUT,
    HTTP_API_INPUT,
    SPATIAL_SERVICE_INPUT,
    KAFKA_INPUT,
    TDENGINE_TMQ_INPUT,
    JOIN,
    GEOMETRY_CONSTRUCT,
    SPATIAL_TRANSFORM,
    GEOMETRY_VALIDATE,
    GEOMETRY_REPAIR,
    GEOMETRY_DERIVE,
    GEOMETRY_SIMPLIFY,
    SPATIAL_NEAREST,
    SPATIAL_SUMMARIZE_WITHIN,
    SPATIAL_OVERLAY,
    TRACK_RECONSTRUCT,
    TRACK_MOTION_STATISTICS,
    TRACK_FIND_DWELL,
    TRACK_DETECT_INCIDENTS,
    SPATIAL_BIN_AGGREGATE,
    SPATIAL_DENSITY,
    SPATIAL_HOT_SPOTS,
    SPATIAL_MULTI_VARIABLE_GRID,
    SPATIAL_ENRICH_FROM_GRID,
    SPATIAL_GROUP_BY_PROXIMITY,
    TRACE_PROXIMITY_EVENTS,
    SNAP_TRACKS,
    SPATIAL_SIMILAR_LOCATIONS,
    SPATIAL_DESCRIBE_DATASET,
    SPATIAL_POINT_CLUSTER,
    SPATIAL_CENTER_DISPERSION,
    GEOMETRY_BUFFER,
    GEOMETRY_EXPLODE,
    SPATIAL_MEASURE,
    GEOMETRY_SERIALIZE,
    SPATIAL_CLIP,
    SPATIAL_AGGREGATE,
    SPATIAL_JOIN,
    STREAM_JOIN,
    RENAME,
    FILTER,
    SQL_TRANSFORM,
    SELECT_COLUMNS,
    DERIVE_COLUMNS,
    TYPE_CAST,
    AGGREGATE,
    UNION,
    DEDUPLICATE,
    NULL_HANDLING,
    VALUE_MAPPING,
    MASK_FIELDS,
    JSON_EXTRACT,
    WINDOW,
    TOP_N,
    MODEL_OUTPUT,
    MODEL_SNAPSHOT_SYNC_OUTPUT,
    JDBC_OUTPUT,
    JDBC_SNAPSHOT_SYNC_OUTPUT,
    KAFKA_OUTPUT,
    FILE_OUTPUT;

    /** Canvas protocol minor version in which this node type was introduced. */
    public int introducedInMinorVersion() {
        return switch (this) {
            case SQL_TRANSFORM -> 1;
            case GEOMETRY_DERIVE -> 9;
            case GEOMETRY_SIMPLIFY -> 10;
            case SPATIAL_NEAREST -> 11;
            case SPATIAL_SUMMARIZE_WITHIN -> 12;
            case SPATIAL_OVERLAY -> 13;
            case TRACK_RECONSTRUCT -> 14;
            case TRACK_MOTION_STATISTICS -> 15;
            case TRACK_FIND_DWELL -> 16;
            case TRACK_DETECT_INCIDENTS -> 17;
            case SPATIAL_BIN_AGGREGATE -> 18;
            case SPATIAL_DENSITY -> 68;
            case SPATIAL_HOT_SPOTS -> 69;
            case SPATIAL_MULTI_VARIABLE_GRID -> 70;
            case SPATIAL_ENRICH_FROM_GRID -> 71;
            case SPATIAL_GROUP_BY_PROXIMITY -> 72;
            case TRACE_PROXIMITY_EVENTS -> 73;
            case SNAP_TRACKS -> 74;
            case SPATIAL_SIMILAR_LOCATIONS -> 75;
            case SPATIAL_DESCRIBE_DATASET -> 76;
            case SPATIAL_POINT_CLUSTER -> 19;
            case SPATIAL_CENTER_DISPERSION -> 20;
            default -> 0;
        };
    }
}

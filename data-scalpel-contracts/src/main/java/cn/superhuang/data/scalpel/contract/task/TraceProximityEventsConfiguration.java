package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("ArcGIS 风格的有界 Point 轨迹邻近传播追踪；按空间、时间和同值属性约束发现首次接触，并按最大传播深度输出事件及可选后续轨迹。")
public record TraceProximityEventsConfiguration(
        @JsonPropertyDescription("包含全部时序 Point 观测的有界 Canvas 逻辑表名。")
        String sourceTableName,
        @JsonPropertyDescription("来源表中带完整 CRS 的 XY Point Geometry 字段名。")
        String pointGeometryColumnName,
        @JsonPropertyDescription("区分移动实体的 STRING 字段名；实体 ID 匹配区分大小写。")
        String entityIdColumnName,
        @JsonPropertyDescription("观测时间 TIMESTAMP 字段名；NULL 时间观测不参与追踪。")
        String timeColumnName,
        @JsonPropertyDescription("PLANAR 使用投影 CRS；GEODESIC 仅使用 EPSG:4326 XY。")
        SpatialDistanceMethod distanceMethod,
        @JsonPropertyDescription("形成邻近事件所允许的有限正空间距离。")
        Double spatialSearchDistance,
        @JsonPropertyDescription("空间搜索距离的单位。")
        SpatialDistanceUnit spatialSearchDistanceUnit,
        @JsonPropertyDescription("形成邻近事件所允许的非负时间差整数。")
        Long temporalSearchDistance,
        @JsonPropertyDescription("时间搜索距离单位；月和年按 Spark 会话时区日历区间计算。")
        SpatialGroupByProximityTemporalUnit temporalSearchDistanceUnit,
        @JsonPropertyDescription("以配置内实体 ID 或另一张上游表提供起始实体。")
        TraceProximityInterestSource interestSource,
        @JsonPropertyDescription("ENTITY_IDS 模式下的 1～256 个起始实体；值不会进入安全摘要。")
        List<TraceProximityEntityOfInterest> entitiesOfInterest,
        @JsonPropertyDescription("TABLE 模式下提供起始实体的有界上游逻辑表名。")
        String entitiesOfInterestTableName,
        @JsonPropertyDescription("TABLE 模式下的 STRING 实体 ID 字段名。")
        String interestEntityIdColumnName,
        @JsonPropertyDescription("TABLE 模式下可选的 TIMESTAMP 开始时间字段；null 表示所有实体从 Unix Epoch 开始。")
        String interestStartTimeColumnName,
        @JsonPropertyDescription("最大传播深度，必须为 1～32；起始实体为深度 0。")
        Integer maxTraceDepth,
        @JsonPropertyDescription("0～8 个同值约束字段；两条观测仅在这些字段全部相等时形成邻近事件。")
        List<String> attributeMatchColumns,
        @JsonPropertyDescription("是否额外输出每个已追踪实体从首次接触或配置开始时间起的全部观测。")
        boolean includeTracks,
        @JsonPropertyDescription("首次邻近事件结果的 Canvas 逻辑表名。")
        String outputTableName,
        @JsonPropertyDescription("includeTracks=true 时必填的后续轨迹结果表名。")
        String tracksOutputTableName,
        @JsonPropertyDescription("首次事件结果追加的上游实体 ID 字段名。")
        String fromEntityIdColumnName,
        @JsonPropertyDescription("首次事件结果追加的下游实体 ID 字段名。")
        String toEntityIdColumnName,
        @JsonPropertyDescription("事件结果和可选轨迹结果追加的传播深度 LONG 字段名。")
        String depthColumnName,
        @JsonPropertyDescription("首次事件结果追加的持续接触时长分钟数字段名。")
        String durationMinutesColumnName,
        @JsonPropertyDescription("首次事件结果追加的首次接触 TIMESTAMP 字段名。")
        String eventTimeColumnName
) {
    public static final int MAX_ENTITIES_OF_INTEREST = 256;
    public static final int MAX_ATTRIBUTE_MATCH_COLUMNS = 8;
    public static final int MAX_TRACE_DEPTH = 32;

    public TraceProximityEventsConfiguration {
        entitiesOfInterest = entitiesOfInterest == null ? null : List.copyOf(entitiesOfInterest);
        attributeMatchColumns = attributeMatchColumns == null ? null : List.copyOf(attributeMatchColumns);
    }
}

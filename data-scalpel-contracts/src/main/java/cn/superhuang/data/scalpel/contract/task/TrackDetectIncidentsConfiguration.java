package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("有界批处理轨迹的事件检测配置；先按轨迹标识、时间和边界形成有序分段，再按开始/结束条件识别事件。输出保留来源字段并追加事件 ID、活动标记、起止时间、持续时间，以及生命周期模式下的状态字段。")

public record TrackDetectIncidentsConfiguration(
        @JsonPropertyDescription("当前操作读取的有界上游 Canvas 逻辑表名；必须引用此前节点已经产生的可用输出表，流式输入不受支持。")
        String sourceTableName,
        @JsonPropertyDescription("可选的 Point Geometry 字段名；配置最大空间间隔、轨迹运动窗口或 Point 相对观测坐标时必填。距离/速度/加速度要求 EPSG:4326 XY；坐标标量保留来源 CRS 数值。空 Geometry 会形成新的轨迹分段或使对应坐标返回 NULL。")
        String pointGeometryColumnName,
        @JsonPropertyDescription("共同标识一条轨迹的 1 至 8 个来源字段；字段不能重复或使用 Geometry，列表顺序参与轨迹身份定义。")
        List<String> trackIdColumns,
        @JsonPropertyDescription("轨迹观测时间字段名；必须是 TIMESTAMP。生命周期模式会丢弃该字段为 NULL 的记录。")
        String timeColumnName,
        @JsonPropertyDescription("空间分段的距离计算方式；配置最大空间间隔时必填，PLANAR 使用来源 CRS 坐标，GEODESIC 使用 EPSG:4326 XY 测地距离。")
        SpatialDistanceMethod distanceMethod,
        @JsonPropertyDescription("必填的轨迹分段边界；时间间隔、空间间隔和固定时间窗口分别生效，均未配置时仍按轨迹标识形成一个连续分段。")
        TrackBoundaryConfiguration boundaries,
        @JsonPropertyDescription("必填的事件开始条件；LEGACY 在条件由不成立变为成立时触发新事件，CONDITION_LIFECYCLE 在每个结束区段中取首个满足条件且不同时满足结束条件的记录作为 Started。可引用来源字段及 conditionWindows/conditionScalars 产生的绑定字段。")
        CanvasFilterCondition startCondition,
        @JsonPropertyDescription("在每条轨迹的有序记录上判定事件结束的可选过滤条件；LEGACY 语义下为空表示首次开始后持续到分段末尾，CONDITION_LIFECYCLE 语义下为空表示 startCondition 首次不成立时结束。")
        CanvasFilterCondition endCondition,
        @JsonPropertyDescription("必填的结果范围：INCIDENTS_ONLY 按 incidentFlagColumnName=true 过滤；ALL_EVENTS 保留全部已参与分析的输入记录。生命周期模式的 Ended 边界记录状态为 Ended 但活动标记为 false，因此不在 INCIDENTS_ONLY 中。")
        TrackIncidentResultMode resultMode,
        @JsonPropertyDescription("当前操作产生的 Canvas 逻辑表名；必须在任务定义内唯一，后续节点通过该值引用结果。")
        String outputTableName,
        @JsonPropertyDescription("必填的事件 ID 输出字段名；同一事件记录共享基于轨迹、分段和事件序号生成的 64 位 SHA-256 十六进制值，非事件记录为 NULL。")
        String incidentIdColumnName,
        @JsonPropertyDescription("必填的事件活动标记输出字段名；LEGACY 的结束条件命中行仍为 true，生命周期模式仅 Started/OnGoing 行为 true，Ended 和事件外记录为 false。")
        String incidentFlagColumnName,
        @JsonPropertyDescription("必填的事件开始时间输出字段名；事件记录取该事件首个活动观测的时间，事件外记录为 NULL。")
        String incidentStartTimeColumnName,
        @JsonPropertyDescription("必填的事件结束时间输出字段名；LEGACY 取事件最后一个活动观测时间；生命周期模式仅检测到 Ended 后有值，未结束事件为 NULL。")
        String incidentEndTimeColumnName,
        @JsonPropertyDescription("必填的 DOUBLE 事件持续时间输出字段名；LEGACY 对事件内各行输出完整事件时长，生命周期模式输出从事件开始到当前 Started、OnGoing 或 Ended 记录的累计时长，事件外为 NULL。")
        String incidentDurationColumnName,
        @JsonPropertyDescription("必填的事件持续时间输出单位，用于把时间戳微秒差换算为输出值。")
        SpatialDurationUnit incidentDurationUnit,
        @JsonPropertyDescription("事件状态机语义；null 兼容旧定义并按 LEGACY 处理。CONDITION_LIFECYCLE 才会使用状态字段、附加排序字段和条件窗口。")
        TrackIncidentSemantics incidentSemantics,
        @JsonPropertyDescription("生命周期模式必填并追加的事件状态字段名；值为 Started、OnGoing、Ended 或 NULL。LEGACY 模式不生成该字段。")
        String incidentStatusColumnName,
        @JsonPropertyDescription("生命周期模式中时间相同记录的附加升序排序字段；按列表顺序、NULL 优先排序，不能重复或使用 Geometry，且轨迹标识、时间和这些字段的组合在运行数据中必须唯一。null 按空列表处理，LEGACY 模式忽略。")
        List<String> orderByColumns,
        @JsonPropertyDescription("生命周期模式下供开始和结束条件引用的窗口聚合绑定；null 按空列表处理。FIELD 读取入口原始字段，TRACK_DISTANCE 读取 WGS84 累计距离，TRACK_SPEED/TRACK_ACCELERATION 读取逐观测速度/加速度。绑定不加入最终输出，LEGACY 模式忽略。FIELD 要求 Canvas 4.46，三种轨迹运动来源依次要求 4.63～4.65。")
        List<TrackIncidentWindow> conditionWindows,
        @JsonPropertyDescription("生命周期模式下供开始和结束条件引用的轨迹标量绑定；null 按空列表处理。起始/当前时间使用 Unix Epoch 毫秒，持续时间使用毫秒，观测序号从 0 开始；4.67 起可读取相对观测的 Point X/Y 坐标。均以当前 DataScalpel 轨迹片段为边界。绑定不加入最终输出，LEGACY 模式忽略，非空配置要求 Canvas 4.66，坐标来源要求 4.67。")
        List<TrackIncidentScalar> conditionScalars
) {
    public static final int MAX_TRACK_ID_COLUMNS = 8;

    public TrackDetectIncidentsConfiguration {
        trackIdColumns = trackIdColumns == null ? null : List.copyOf(trackIdColumns);
        orderByColumns = orderByColumns == null ? List.of() : List.copyOf(orderByColumns);
        conditionWindows = conditionWindows == null ? List.of() : List.copyOf(conditionWindows);
        conditionScalars = conditionScalars == null ? List.of() : List.copyOf(conditionScalars);
    }

    public TrackIncidentSemantics effectiveIncidentSemantics() {
        return incidentSemantics == null ? TrackIncidentSemantics.LEGACY : incidentSemantics;
    }

    public boolean usesLifecycleOptions() {
        return effectiveIncidentSemantics() != TrackIncidentSemantics.LEGACY
                || incidentStatusColumnName != null || !orderByColumns.isEmpty();
    }

    public TrackDetectIncidentsConfiguration(
            String sourceTableName, String pointGeometryColumnName, List<String> trackIdColumns,
            String timeColumnName, SpatialDistanceMethod distanceMethod, TrackBoundaryConfiguration boundaries,
            CanvasFilterCondition startCondition, CanvasFilterCondition endCondition, TrackIncidentResultMode resultMode,
            String outputTableName, String incidentIdColumnName, String incidentFlagColumnName,
            String incidentStartTimeColumnName, String incidentEndTimeColumnName, String incidentDurationColumnName,
            SpatialDurationUnit incidentDurationUnit, TrackIncidentSemantics incidentSemantics,
            String incidentStatusColumnName, List<String> orderByColumns, List<TrackIncidentWindow> conditionWindows
    ) {
        this(sourceTableName, pointGeometryColumnName, trackIdColumns, timeColumnName, distanceMethod, boundaries,
                startCondition, endCondition, resultMode, outputTableName, incidentIdColumnName, incidentFlagColumnName,
                incidentStartTimeColumnName, incidentEndTimeColumnName, incidentDurationColumnName, incidentDurationUnit,
                incidentSemantics, incidentStatusColumnName, orderByColumns, conditionWindows, List.of());
    }

    public TrackDetectIncidentsConfiguration(
            String sourceTableName, String pointGeometryColumnName, List<String> trackIdColumns,
            String timeColumnName, SpatialDistanceMethod distanceMethod, TrackBoundaryConfiguration boundaries,
            CanvasFilterCondition startCondition, CanvasFilterCondition endCondition, TrackIncidentResultMode resultMode,
            String outputTableName, String incidentIdColumnName, String incidentFlagColumnName,
            String incidentStartTimeColumnName, String incidentEndTimeColumnName, String incidentDurationColumnName,
            SpatialDurationUnit incidentDurationUnit, TrackIncidentSemantics incidentSemantics,
            String incidentStatusColumnName, List<String> orderByColumns
    ) {
        this(sourceTableName, pointGeometryColumnName, trackIdColumns, timeColumnName, distanceMethod, boundaries,
                startCondition, endCondition, resultMode, outputTableName, incidentIdColumnName, incidentFlagColumnName,
                incidentStartTimeColumnName, incidentEndTimeColumnName, incidentDurationColumnName, incidentDurationUnit,
                incidentSemantics, incidentStatusColumnName, orderByColumns, List.of(), List.of());
    }

    public TrackDetectIncidentsConfiguration(
            String sourceTableName, String pointGeometryColumnName, List<String> trackIdColumns,
            String timeColumnName, SpatialDistanceMethod distanceMethod, TrackBoundaryConfiguration boundaries,
            CanvasFilterCondition startCondition, CanvasFilterCondition endCondition, TrackIncidentResultMode resultMode,
            String outputTableName, String incidentIdColumnName, String incidentFlagColumnName,
            String incidentStartTimeColumnName, String incidentEndTimeColumnName, String incidentDurationColumnName,
            SpatialDurationUnit incidentDurationUnit
    ) {
        this(sourceTableName, pointGeometryColumnName, trackIdColumns, timeColumnName, distanceMethod, boundaries,
                startCondition, endCondition, resultMode, outputTableName, incidentIdColumnName, incidentFlagColumnName,
                incidentStartTimeColumnName, incidentEndTimeColumnName, incidentDurationColumnName, incidentDurationUnit,
                null, null, List.of(), List.of(), List.of());
    }
}

package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("事件检测结果范围：INCIDENTS_ONLY 只保留事件活动标记为 true 的记录；ALL_EVENTS 保留所有参与分析的记录，并以事件字段区分活动、结束和事件外记录。")
public enum TrackIncidentResultMode {
    INCIDENTS_ONLY,
    ALL_EVENTS
}

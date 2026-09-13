package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** A node-local scalar track binding used only by Detect Incidents predicates. */
@JsonClassDescription("生命周期事件条件使用的轨迹标量绑定；按当前轨迹片段计算时间、序号，或读取相对观测的 Point X/Y 坐标。结果只供条件表达式引用，不写入节点输出。")
public record TrackIncidentScalar(
        @JsonPropertyDescription("标量结果在 startCondition/endCondition 中使用的临时字段名；必须为 1 至 128 位字母、数字或下划线且不能以数字开头，不能与来源字段、窗口指标、平台临时字段或其他标量绑定重名。")
        String bindingName,
        @JsonPropertyDescription("必填的轨迹标量来源：TRACK_START_TIME 和 TRACK_CURRENT_TIME 为 Unix Epoch 毫秒，TRACK_DURATION 为片段起点至当前观测的毫秒数，TRACK_INDEX 为片段内从 0 开始的观测序号；TRACK_POINT_X_AT/TRACK_POINT_Y_AT 读取相对观测的 Point 坐标。")
        Source source,
        @JsonPropertyDescription("Point 坐标来源必填的相对观测偏移：0 为当前，负数为过去，正数为未来，超出片段返回 NULL；其他来源忽略并保留该草稿值。坐标来源从 Canvas 4.67 引入。")
        Integer offset
) {
    public enum Source {
        TRACK_START_TIME,
        TRACK_DURATION,
        TRACK_CURRENT_TIME,
        TRACK_INDEX,
        TRACK_POINT_X_AT,
        TRACK_POINT_Y_AT
    }

    public TrackIncidentScalar(String bindingName, Source source) {
        this(bindingName, source, null);
    }

    public boolean isPointCoordinate() {
        return source == Source.TRACK_POINT_X_AT || source == Source.TRACK_POINT_Y_AT;
    }
}

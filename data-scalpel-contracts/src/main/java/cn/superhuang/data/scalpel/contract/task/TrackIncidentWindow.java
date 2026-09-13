package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** A node-local condition binding over the half-open observation range [start, end). */
@JsonClassDescription("生命周期事件条件使用的临时窗口聚合；可读取入口字段、WGS84 累计轨迹距离、逐观测速度或逐观测加速度。范围为左闭右开 [startOffset, endOffset)，结果只供条件表达式引用，不写入节点输出。")
public record TrackIncidentWindow(
        @JsonPropertyDescription("窗口结果在 startCondition/endCondition 中使用的临时字段名；必须为 1 至 128 位字母、数字或下划线且不能以数字开头，不能与来源字段、平台临时字段或其他绑定重名。")
        String bindingName,
        @JsonPropertyDescription("FIELD 来源必填的入口表原始字段名，不能引用另一个窗口绑定；TRACK_DISTANCE/TRACK_SPEED/TRACK_ACCELERATION 来源忽略该字段并允许为空。")
        String sourceColumnName,
        @JsonPropertyDescription("必填的聚合类型：COUNT 统计窗口内来源字段非 NULL 数；SUM/MEAN/MIN/MAX、FIRST/LAST、STDDEV_POP/VARIANCE_POP 使用 Spark 对应聚合语义。")
        Kind kind,
        @JsonPropertyDescription("必填的相对行起始偏移，包含该位置；0 表示当前记录，负数表示历史记录，正数表示未来记录。必须小于 endOffset，且不能为 -2147483648。")
        Integer startOffset,
        @JsonPropertyDescription("必填的相对行结束偏移，不包含该位置；必须大于 startOffset。例如 [-5, 0) 读取当前行之前最多 5 条记录，[0, 1) 只读取当前记录。")
        Integer endOffset,
        @JsonPropertyDescription("窗口值来源；null 按 FIELD 兼容旧定义。FIELD 聚合 sourceColumnName；TRACK_DISTANCE 聚合逐观测 WGS84 累计距离（米，Canvas 4.63）；TRACK_SPEED 聚合逐观测速度（米/秒，Canvas 4.64）；TRACK_ACCELERATION 聚合逐观测加速度（米/秒²，Canvas 4.65）。")
        Source source
) {
    public enum Source { FIELD, TRACK_DISTANCE, TRACK_SPEED, TRACK_ACCELERATION }
    public enum Kind { COUNT, SUM, MEAN, MIN, MAX, FIRST, LAST, STDDEV_POP, VARIANCE_POP }

    public TrackIncidentWindow(String bindingName, String sourceColumnName, Kind kind,
                               Integer startOffset, Integer endOffset) {
        this(bindingName, sourceColumnName, kind, startOffset, endOffset, null);
    }

    public Source effectiveSource() {
        return source == null ? Source.FIELD : source;
    }
}

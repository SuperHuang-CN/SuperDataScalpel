package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** A scalar/geometry observation at an offset inside the same track and fixed time period. */
@JsonClassDescription("轨迹分段表达式使用的一个相对观测绑定；按同一轨迹、同一固定周期和完整排序键取值，越过窗口边界时结果为 NULL。")
public record TrackFieldWindowBinding(
        @JsonPropertyDescription("表达式变量名；须匹配 [A-Za-z_][A-Za-z0-9_]{0,127}，大小写不敏感唯一，且不能覆盖入口字段名。")
        String name,
        @JsonPropertyDescription("来源字段名；必须存在于当前操作所引用的上游逻辑表 Schema 中。")
        String sourceColumnName,
        @JsonPropertyDescription("-1000～1000 的相对观测偏移；0 为当前、负数为之前、正数为之后，越过轨迹或固定周期边界时绑定值为 NULL。")
        Integer offset
) { }

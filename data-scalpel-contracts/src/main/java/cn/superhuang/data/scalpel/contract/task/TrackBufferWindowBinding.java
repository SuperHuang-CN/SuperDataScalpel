package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Inclusive observation offsets, evaluated before optional gap/expression endpoint sharing. */
@JsonClassDescription("轨迹面积缓冲表达式使用的一个包含式观测窗口统计；按同一轨迹、同一固定周期和完整排序键计算，在普通 gap 与表达式切分之前完成。")
public record TrackBufferWindowBinding(
        @JsonPropertyDescription("表达式变量名；在当前缓冲配置内大小写不敏感唯一且不能覆盖入口字段。")
        String name,
        @JsonPropertyDescription("来源字段名；必须存在于当前操作所引用的上游逻辑表 Schema 中。")
        String sourceColumnName,
        @JsonPropertyDescription("相对当前轨迹观测位置的包含式起始偏移；负数表示之前的观测。")
        Integer startOffset,
        @JsonPropertyDescription("相对当前轨迹观测位置的包含式结束偏移；必须不小于 startOffset。")
        Integer endOffset,
        @JsonPropertyDescription("窗口内聚合类型；遵循 TrackSummaryStatisticKind 的字段、NULL 和结果类型规则。")
        TrackSummaryStatisticKind statistic
) { }

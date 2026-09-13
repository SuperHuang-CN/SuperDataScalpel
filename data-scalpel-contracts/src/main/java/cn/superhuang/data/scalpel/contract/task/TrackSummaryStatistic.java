package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("每个轨迹片段或驻留结果的一项摘要统计。COUNT 不读取来源字段；COUNT_FIELD 统计非 NULL；ANY 返回任意非 NULL 样本且选择不稳定，轨迹重建仅允许字符串、驻留允许字符串或数值并保留原类型；FIRST/LAST 使用所属算法的完整排序键；STDDEV/VARIANCE 为样本统计。")
public record TrackSummaryStatistic(
        @JsonPropertyDescription("当前统计项的稳定 ID。")
        String statisticId,
        @JsonPropertyDescription("统计类型；字段要求、NULL 处理和结果类型见 TrackSummaryStatisticKind。")
        TrackSummaryStatisticKind kind,
        @JsonPropertyDescription("来源字段名；COUNT 不使用且应为空，其他统计必须引用入口字段。数值统计要求数值字段；ANY 在轨迹重建中要求字符串，在驻留中允许字符串或数值。")
        String sourceColumnName,
        @JsonPropertyDescription("当前操作生成的输出字段名；必须符合字段命名规则且在输出 Schema 中唯一。")
        String outputColumnName
) {
}

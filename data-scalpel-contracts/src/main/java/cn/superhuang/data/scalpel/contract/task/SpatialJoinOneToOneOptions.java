package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("Canvas 4.58 起的一对一空间连接配置。SUMMARIZE_MATCHES 使用 Join Count 和可选数值统计；KEEP_ONE 使用确定性保留规则。非当前模式的字段作为草稿保留但不参与执行。")
public record SpatialJoinOneToOneOptions(
        @JsonPropertyDescription("必填模式：汇总全部匹配项或确定性保留一项。")
        SpatialJoinOneToOneMode mode,
        @JsonPropertyDescription("SUMMARIZE_MATCHES 必填的匹配记录数字段名；0 表示没有匹配项。")
        String joinCountColumnName,
        @JsonPropertyDescription("SUMMARIZE_MATCHES 的有序数值统计，最多 32 项；允许为空数组，仅输出 Join Count。")
        List<SpatialJoinSummaryStatistic> summaryStatistics,
        @JsonPropertyDescription("KEEP_ONE 必填的确定性保留规则；汇总模式下作为非活动草稿保留。")
        SpatialJoinKeepRule keepRule
) {
    public static final int MAX_SUMMARY_STATISTICS = 32;

    public SpatialJoinOneToOneOptions {
        summaryStatistics = summaryStatistics == null ? null : List.copyOf(summaryStatistics);
    }
}

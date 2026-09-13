package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("一对一空间连接对右侧连接表数值字段执行的一项汇总统计。")
public record SpatialJoinSummaryStatistic(
        @JsonPropertyDescription("必填且在当前一对一配置内唯一的 UUID 字符串，只用于稳定识别和排序配置。")
        String statisticId,
        @JsonPropertyDescription("必填统计类型：SUM、MIN、MAX、MEAN 或样本 STDDEV。")
        SpatialJoinSummaryStatisticKind kind,
        @JsonPropertyDescription("右侧连接表中的必填数值字段；Geometry 和非数值字段不支持。")
        String sourceColumnName,
        @JsonPropertyDescription("必填输出字段名，必须与目标投影字段、Join Count 及其他统计字段唯一。")
        String outputColumnName
) {
}

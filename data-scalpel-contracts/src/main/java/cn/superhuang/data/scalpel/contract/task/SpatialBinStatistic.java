package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("格网聚合中的一个统计项；COUNT 统计参与点，COUNT_FIELD 统计字段非 NULL 数，ANY 取不保证稳定的字符串样本，其余类型对数值字段计算 Spark 聚合。")
public record SpatialBinStatistic(
        @JsonPropertyDescription("必填且在 statistics 内唯一的 UUID 字符串，用于稳定识别本统计配置，不写入结果。")
        String statisticId,
        @JsonPropertyDescription("必填的统计类型；COUNT/COUNT_FIELD 输出 LONG，ANY 保留字符串类型，SUM/MEAN/MIN/MAX/RANGE/STDDEV/VARIANCE 的实际输出类型由 Spark Analyzer 根据来源类型确定。")
        SpatialBinStatisticKind kind,
        @JsonPropertyDescription("COUNT 必须为 NULL；其他统计必填且必须是来源字段。COUNT_FIELD 可统计任意可计数字段，ANY 只支持 STRING，其余类型只支持数值字段。")
        String sourceColumnName,
        @JsonPropertyDescription("必填的统计输出字段名；必须与其他统计、格网、时间窗口及分组输出字段按大小写不敏感规则保持唯一。")
        String outputColumnName
) {
}

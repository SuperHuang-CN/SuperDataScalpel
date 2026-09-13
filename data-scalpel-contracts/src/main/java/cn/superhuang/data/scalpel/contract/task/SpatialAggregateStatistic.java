package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("空间 Dissolve 的一个标量汇总项；只读取原始来源表字段，不读取其他统计或 Geometry 聚合结果。")
public record SpatialAggregateStatistic(
        @JsonPropertyDescription("必填且在当前 Dissolve 配置内唯一的 UUID 字符串，只用于稳定识别和排序配置，不写入结果。")
        String statisticId,
        @JsonPropertyDescription("必填统计类型。COUNT_FIELD 输出 LONG；ANY 保留字符串类型；MEAN/STDDEV/VARIANCE 输出 DOUBLE；其他数值统计由 Spark Analyzer 推导类型。")
        SpatialAggregateStatisticKind kind,
        @JsonPropertyDescription("必填来源标量字段。COUNT_FIELD 可统计任意非 Geometry 字段，ANY 只支持 STRING，其余类型要求数值字段。")
        String sourceColumnName,
        @JsonPropertyDescription("必填结果字段名；与分组字段、Geometry 聚合字段、要素计数字段及其他统计按大小写不敏感规则唯一。")
        String outputColumnName
) {
}

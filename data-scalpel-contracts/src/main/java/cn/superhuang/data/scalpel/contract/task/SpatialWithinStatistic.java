package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("范围内汇总的一个统计项。每个相交的被汇总要素提供一次观测；COUNT、LENGTH_WITHIN、AREA_WITHIN 不读取标量来源字段，其余按 kind 读取字段。可选总量分摊与交叠比例加权只适用于明确线/面类型及受支持统计组合，不能同时启用。")
public record SpatialWithinStatistic(
        @JsonPropertyDescription("统计项稳定 UUID，必须可解析为 UUID 且在本节点 statistics 内唯一；调整排序后保持不变，用于编辑、诊断和血缘定位。")
        String statisticId,
        @JsonPropertyDescription("必填统计类型。COUNT 计命中要素行；COUNT_FIELD 计字段非 null；ANY 取任意非空字符串；SUM/MEAN/MIN/MAX/RANGE/STDDEV/VARIANCE 聚合字段；LENGTH_WITHIN/AREA_WITHIN 量测相交片段。")
        SpatialWithinStatisticKind kind,
        @JsonPropertyDescription("被汇总表的标量来源字段名。COUNT、LENGTH_WITHIN、AREA_WITHIN 必须为 null；ANY 要求 STRING；SUM、MEAN、RANGE、STDDEV、VARIANCE 及所有 APPORTION_TOTAL 组合要求数值字段；MIN/MAX 原值模式可使用 Spark 支持排序的非 Geometry 标量。")
        String sourceColumnName,
        @JsonPropertyDescription("统计结果字段名，按大小写不敏感规则不能与其他主结果字段重名。关联组表模式下同名统计同时出现在主表总体统计和组表逐组统计中。")
        String outputColumnName,
        @JsonPropertyDescription("可选数量处理。null 或 ORIGINAL_VALUE 直接统计原值；APPORTION_TOTAL 仅适用于明确线/面和 SUM/MEAN/MIN/MAX/RANGE/STDDEV/VARIANCE，先按 p=区域内相交长度或面积/完整来源长度或面积计算 x'=p×x。零或非有限来源测度使 p 为 null。")
        SpatialWithinValueTreatment valueTreatment,
        @JsonPropertyDescription("可选统计权重。null 或 NONE 使用普通聚合；INTERSECTION_FRACTION 仅适用于明确线/面的原值 MEAN、VARIANCE、STDDEV，权重 p 为相交测度/完整来源测度。无有效正权重时结果为 null；不能与 APPORTION_TOTAL 同时使用。")
        SpatialWithinWeighting weighting
) {
    public SpatialWithinStatistic(String statisticId, SpatialWithinStatisticKind kind,
                                  String sourceColumnName, String outputColumnName) {
        this(statisticId, kind, sourceColumnName, outputColumnName, null, null);
    }

    public boolean apportionsTotal() {
        return valueTreatment == SpatialWithinValueTreatment.APPORTION_TOTAL;
    }

    public boolean usesGeographicWeight() {
        return weighting == SpatialWithinWeighting.INTERSECTION_FRACTION;
    }

    public boolean requiresWeightedDispersionVersion() {
        return usesGeographicWeight() && (kind == SpatialWithinStatisticKind.VARIANCE
                || kind == SpatialWithinStatisticKind.STDDEV);
    }

    public boolean requiresExplicitStatisticsVersion() {
        return valueTreatment != null || weighting != null
                || kind == SpatialWithinStatisticKind.COUNT_FIELD || kind == SpatialWithinStatisticKind.ANY;
    }
}

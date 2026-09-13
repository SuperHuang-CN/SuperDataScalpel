package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("范围内统计权重。NONE 或 null 执行普通聚合；INTERSECTION_FRACTION 使用线/面在区域内的相交长度/面积占完整来源测度的比例作为权重，仅支持原值 MEAN、VARIANCE、STDDEV。NULL、非有限值和非正权重观测被排除；无有效权重时返回 null。")
public enum SpatialWithinWeighting {
    NONE,
    INTERSECTION_FRACTION
}

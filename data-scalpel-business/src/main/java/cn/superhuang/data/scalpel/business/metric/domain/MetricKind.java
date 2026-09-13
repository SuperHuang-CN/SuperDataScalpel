package cn.superhuang.data.scalpel.business.metric.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "指标类型：ATOMIC 原子指标，DERIVED 派生指标，COMPOSITE 复合指标。当前类型用于治理分类，不执行计算逻辑。")
public enum MetricKind { ATOMIC, DERIVED, COMPOSITE }

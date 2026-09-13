package cn.superhuang.data.scalpel.business.metric.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "指标生命周期：DRAFT 从未发布，PUBLISHED 当前已发布，DISABLED 已停用但保留发布历史和当前版本。再次发布草稿可恢复为 PUBLISHED。")
public enum MetricStatus { DRAFT, PUBLISHED, DISABLED }

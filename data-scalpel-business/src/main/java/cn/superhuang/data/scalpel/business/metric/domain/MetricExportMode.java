package cn.superhuang.data.scalpel.business.metric.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "指标导出口径来源：DRAFT 导出当前可编辑草稿，PUBLISHED 导出当前发布版本。")
public enum MetricExportMode { DRAFT, PUBLISHED }

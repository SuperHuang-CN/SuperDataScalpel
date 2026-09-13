package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("中心分析结果组织模式。ANALYSIS_TABLES 为每个 analysis 输出独立逻辑表，支持具体点/线/面家族、新数值算法、容量限制和中央要素原字段投影；LEGACY_WIDE 把所有分析 Geometry 放入一张表且只接受 POINT。null 按 LEGACY_WIDE 兼容处理，不会自动迁移旧结果。")
public enum SpatialCenterResultMode { ANALYSIS_TABLES, LEGACY_WIDE }

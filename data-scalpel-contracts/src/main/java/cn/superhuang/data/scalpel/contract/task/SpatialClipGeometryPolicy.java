package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间裁剪结果 Geometry 策略。SOURCE_FAMILY_2D 保持来源点/线/面家族，过滤边界接触产生的低拓扑维度片段并规范化为二维 Multi；LEGACY_ANY_DIMENSION 保留旧版任意拓扑维度的通用 Geometry。该策略不提供 ArcGIS 容差、吸附或自动修复；多条 Mask 是否合并由 maskCombination 独立控制。")
public enum SpatialClipGeometryPolicy {
    SOURCE_FAMILY_2D,
    LEGACY_ANY_DIMENSION
}

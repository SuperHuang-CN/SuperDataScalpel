package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间裁剪多 Mask 组合方式。DISSOLVE_ALL 先按每条来源要素合并全部相交 Mask，再执行一次裁剪，避免重叠 Mask 产生重复区域和重复来源行；PAIRWISE 保留每个来源与每条 Mask 独立相交的旧行为。")
public enum SpatialClipMaskCombination {
    DISSOLVE_ALL,
    PAIRWISE
}

package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("字段数量含义。ORIGINAL_VALUE 直接统计原值，适合率值、指数或无需空间分摊的值；APPORTION_TOTAL 把线/面总量先乘以相交长度/面积占完整来源长度/面积的比例。null 与 ORIGINAL_VALUE 相同以兼容旧定义。分摊不支持点、通用 Geometry、计数、ANY 或形状量测，也不能再叠加交叠比例权重。")
public enum SpatialWithinValueTreatment {
    ORIGINAL_VALUE,
    APPORTION_TOTAL
}

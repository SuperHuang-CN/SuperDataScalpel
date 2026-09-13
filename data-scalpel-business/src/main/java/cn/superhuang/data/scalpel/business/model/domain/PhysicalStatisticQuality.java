package cn.superhuang.data.scalpel.business.model.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "物理统计质量：EXACT 精确值，ESTIMATED 数据库估算值，UNAVAILABLE 不可获取")
public enum PhysicalStatisticQuality {
    EXACT,
    ESTIMATED,
    UNAVAILABLE
}

package cn.superhuang.data.scalpel.business.lineage.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "字段派生方式：DIRECT 直接传递，CALCULATED 经表达式计算，AGGREGATED 经分组或聚合产生。")
public enum LineageFieldDerivationType {
    DIRECT,
    CALCULATED,
    AGGREGATED
}

package cn.superhuang.data.scalpel.business.service.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "线符号样式：SOLID 实线，DASHED 虚线，DOTTED 点线。")
public enum SpatialLinePattern {
    SOLID,
    DASHED,
    DOTTED
}

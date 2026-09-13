package cn.superhuang.data.scalpel.business.service.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "点符号形状：CIRCLE 圆形，SQUARE 方形，TRIANGLE 三角形，STAR 星形。")
public enum SpatialMarkerShape {
    CIRCLE,
    SQUARE,
    TRIANGLE,
    STAR
}

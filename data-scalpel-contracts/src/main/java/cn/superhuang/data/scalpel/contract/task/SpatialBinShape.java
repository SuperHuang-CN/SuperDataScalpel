package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间格网形状：SQUARE 为投影 CRS 下不旋转的方格；HEXAGON 为 flat-top 投影六边形；H3 为 EPSG:4326 原生球面分层单元，不是平面六边形近似。")
public enum SpatialBinShape {
    SQUARE,
    HEXAGON,
    H3
}

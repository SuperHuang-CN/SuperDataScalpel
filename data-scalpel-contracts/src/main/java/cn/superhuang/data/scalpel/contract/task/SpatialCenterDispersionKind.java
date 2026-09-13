package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("中心与离散分析类型。MEAN_CENTER 为 Σw·质心/Σw 的加权平均点；MEDIAN_CENTER 用有停止准则的修正 Weiszfeld 算法求最小加权距离和位置，不是 X/Y 分量中位数；CENTRAL_FEATURE 从真实来源要素中选择到所有正权重代表位置总距离最小者并返回原 Geometry；STANDARD_DISTANCE 生成以平均中心为圆心、k·sqrt(varX+varY) 为半径的圆，是平台扩展；DIRECTIONAL_ELLIPSE 以人口加权协方差矩生成 128 边方向椭圆。当前椭圆公式尚未声明与 ArcGIS 数值完全等价。")
public enum SpatialCenterDispersionKind {
    MEAN_CENTER,
    MEDIAN_CENTER,
    CENTRAL_FEATURE,
    STANDARD_DISTANCE,
    DIRECTIONAL_ELLIPSE
}

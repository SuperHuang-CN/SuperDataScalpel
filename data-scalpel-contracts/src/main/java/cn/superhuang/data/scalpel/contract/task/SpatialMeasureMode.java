package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间距离或面积计算模式。PLANAR 直接在来源 CRS 的二维坐标空间计算，线性结果使用坐标轴单位、面积使用其平方，EPSG:4326 因此得到角度或角度平方并警告；SPHEROID 仅接受 EPSG:4326 XY，长度、周长、距离和 Buffer 距离使用米，面积使用平方米。两种模式都不自动转换 CRS。")
public enum SpatialMeasureMode {
    PLANAR,
    SPHEROID
}

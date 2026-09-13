package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("空间长度、面积和距离的计算方法。PLANAR 直接在来源 CRS 的二维坐标空间中计算，再按配置单位换算；地理 CRS 的平面长度只能使用来源角度单位，角度平方不能换算为面积单位。GEODESIC 使用 WGS84 椭球，仅接受 EPSG:4326 XY，原始长度为米、面积为平方米。两者都不自动转换 CRS。")
public enum SpatialDistanceMethod {
    PLANAR,
    GEODESIC
}

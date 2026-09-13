package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Group By Proximity 的空间关系。INTERSECTS 与 TOUCHES 使用二维拓扑；NEAR_PLANAR 在投影 CRS 中按距离查找；NEAR_GEODESIC 使用 EPSG:4326 XY 的椭球最近距离。")
public enum SpatialGroupByProximitySpatialRelationship {
    INTERSECTS,
    TOUCHES,
    NEAR_PLANAR,
    NEAR_GEODESIC;

    public boolean usesDistance() {
        return this == NEAR_PLANAR || this == NEAR_GEODESIC;
    }
}

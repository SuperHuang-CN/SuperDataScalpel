package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Group By Proximity 的可选无方向时间关系。INTERSECTS 要求两个闭区间重叠；NEAR 还允许两个时间范围之间的间隔不超过阈值。")
public enum SpatialGroupByProximityTemporalRelationship {
    INTERSECTS,
    NEAR;

    public boolean usesDistance() {
        return this == NEAR;
    }
}

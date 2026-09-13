package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("Group By Proximity 的受控对称属性关系。EQUALS 比较同一字段值；ABSOLUTE_DIFFERENCE_AT_MOST 比较同一数值字段的绝对差。")
public enum SpatialGroupByProximityAttributeRelationship {
    EQUALS,
    ABSOLUTE_DIFFERENCE_AT_MOST;

    public boolean usesMaximumDifference() {
        return this == ABSOLUTE_DIFFERENCE_AT_MOST;
    }
}

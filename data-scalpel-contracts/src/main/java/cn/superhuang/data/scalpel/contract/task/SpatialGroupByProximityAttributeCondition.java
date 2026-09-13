package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Group By Proximity 的一项对称属性关系。多项条件与空间、时间关系按 AND 组合。")
public record SpatialGroupByProximityAttributeCondition(
        @JsonPropertyDescription("自连接两侧共同使用的非 Geometry 来源字段名。")
        String columnName,
        @JsonPropertyDescription("必填的受控对称关系。")
        SpatialGroupByProximityAttributeRelationship relationship,
        @JsonPropertyDescription("ABSOLUTE_DIFFERENCE_AT_MOST 必填的有限非负阈值；EQUALS 下作为非活动草稿保留。")
        Double maximumDifference
) {
}

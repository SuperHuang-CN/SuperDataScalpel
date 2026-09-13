package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Group By Proximity 的可选时间关系。同一组字段同时用于自连接两侧；单字段表示瞬时，开始和结束字段表示闭区间。")
public record SpatialGroupByProximityTemporalCondition(
        @JsonPropertyDescription("必填时间关系。")
        SpatialGroupByProximityTemporalRelationship relationship,
        @JsonPropertyDescription("必填的 DATE、TIMESTAMP 或 TIMESTAMP_NTZ 开始/瞬时时间字段。")
        String startColumnName,
        @JsonPropertyDescription("可选结束时间字段；null 表示瞬时，非 null 时必须与开始字段同类型。")
        String endColumnName,
        @JsonPropertyDescription("NEAR 必填的正整数时间邻近阈值；INTERSECTS 下作为非活动草稿保留。")
        Long nearDistance,
        @JsonPropertyDescription("NEAR 必填的时间单位；INTERSECTS 下作为非活动草稿保留。")
        SpatialGroupByProximityTemporalUnit nearDistanceUnit
) {
}

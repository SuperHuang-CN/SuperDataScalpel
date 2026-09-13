package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Canvas 4.59 起空间连接的一项可选时间关系。每侧可用一个时间字段表示瞬时，或用开始/结束字段表示闭区间；它与全部空间、属性条件按 AND 组合。")
public record SpatialJoinTemporalCondition(
        @JsonPropertyDescription("必填的有方向时间关系；左侧是目标要素，右侧是连接要素。")
        SpatialJoinTemporalRelationship relationship,
        @JsonPropertyDescription("左侧目标表的必填 DATE、TIMESTAMP 或 TIMESTAMP_NTZ 开始/瞬时时间字段。")
        String leftStartColumnName,
        @JsonPropertyDescription("左侧目标表的可选结束时间字段；null 表示与 leftStartColumnName 相同的瞬时。非 null 时必须与开始字段同类型。")
        String leftEndColumnName,
        @JsonPropertyDescription("右侧连接表的必填 DATE、TIMESTAMP 或 TIMESTAMP_NTZ 开始/瞬时时间字段。")
        String rightStartColumnName,
        @JsonPropertyDescription("右侧连接表的可选结束时间字段；null 表示与 rightStartColumnName 相同的瞬时。非 null 时必须与开始字段同类型。")
        String rightEndColumnName,
        @JsonPropertyDescription("NEAR/NEAR_BEFORE/NEAR_AFTER 必填的正整数固定时长；其他关系下作为非活动草稿保留。")
        Long nearDistance,
        @JsonPropertyDescription("NEAR/NEAR_BEFORE/NEAR_AFTER 必填的固定时长单位；DAYS 为 24 小时、WEEKS 为 7 天。其他关系下作为非活动草稿保留。")
        SpatialDurationUnit nearDistanceUnit
) {
    public boolean usesNearDistance() {
        return relationship == SpatialJoinTemporalRelationship.NEAR
                || relationship == SpatialJoinTemporalRelationship.NEAR_BEFORE
                || relationship == SpatialJoinTemporalRelationship.NEAR_AFTER;
    }
}

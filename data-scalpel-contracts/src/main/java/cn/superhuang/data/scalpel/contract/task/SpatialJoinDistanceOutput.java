package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("Canvas 4.60 起空间连接的一对多距离输出配置。空间 Near 可输出空间距离，时间 Near 可输出区间间隔；两者同时存在时可同时输出。")
public record SpatialJoinDistanceOutput(
        @JsonPropertyDescription("是否启用距离输出。false 时其余字段作为非活动草稿保留。")
        boolean enabled,
        @JsonPropertyDescription("空间 Near 启用时必填的空间距离字段名；没有空间 Near 时作为非活动草稿保留。")
        String spatialDistanceColumnName,
        @JsonPropertyDescription("空间 Near 启用时必填的空间距离输出单位；没有空间 Near 时作为非活动草稿保留。")
        SpatialDistanceUnit spatialDistanceUnit,
        @JsonPropertyDescription("时间 NEAR/NEAR_BEFORE/NEAR_AFTER 启用时必填的时间差字段名；其他时间关系下作为非活动草稿保留。")
        String temporalDifferenceColumnName,
        @JsonPropertyDescription("时间 Near 启用时必填的固定时长输出单位。")
        SpatialDurationUnit temporalDifferenceUnit
) {
}

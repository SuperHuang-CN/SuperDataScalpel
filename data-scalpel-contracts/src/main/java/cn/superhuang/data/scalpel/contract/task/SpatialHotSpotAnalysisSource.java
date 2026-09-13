package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

@JsonClassDescription("热点分析数值来源；POINT_COUNT 使用格网点数，FIELD_SUM 使用格网内数值字段之和。")
public enum SpatialHotSpotAnalysisSource {
    POINT_COUNT,
    FIELD_SUM
}

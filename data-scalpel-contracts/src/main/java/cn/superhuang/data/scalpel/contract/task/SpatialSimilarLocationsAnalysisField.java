package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("参考表和候选表中同名同类型的数值分析字段，以及结果中的显式字段名。")
public record SpatialSimilarLocationsAnalysisField(
        @JsonPropertyDescription("参考表和候选表中必须同时存在的数值字段名。")
        String columnName,
        @JsonPropertyDescription("该分析字段写入结果表时使用的字段名。")
        String outputColumnName
) {
}

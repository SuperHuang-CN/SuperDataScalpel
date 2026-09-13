package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("只用于结果解释、不参与相似度计算的候选表附加字段。")
public record SpatialSimilarLocationsAppendField(
        @JsonPropertyDescription("候选表中的来源字段名。")
        String sourceColumnName,
        @JsonPropertyDescription("附加字段写入结果表时使用的字段名。")
        String outputColumnName
) {
}

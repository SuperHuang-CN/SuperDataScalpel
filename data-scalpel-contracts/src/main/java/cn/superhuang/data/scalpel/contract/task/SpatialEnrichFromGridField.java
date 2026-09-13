package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("从多变量格网回填到 Point 结果中的一个显式属性字段；数组顺序决定结果字段顺序。")
public record SpatialEnrichFromGridField(
        @JsonPropertyDescription("格网表中的非 Geometry 标量来源字段；同一来源字段在列表中只能出现一次。")
        String sourceColumnName,
        @JsonPropertyDescription("回填到结果 Point 表中的字段名；与 Point 原字段和其他回填字段按大小写不敏感规则唯一。")
        String outputColumnName
) {
}

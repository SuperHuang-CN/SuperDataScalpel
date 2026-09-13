package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("一个参与空间密度计算的数值数量字段；点数密度始终另外计算。")
public record SpatialDensityField(
        @JsonPropertyDescription("节点内稳定且唯一的 UUID，用于编辑、排序和诊断。")
        String fieldId,
        @JsonPropertyDescription("来源点表中的数值字段；NULL 不贡献当前字段的密度。")
        String sourceColumnName,
        @JsonPropertyDescription("当前数量字段对应的 DOUBLE 密度结果字段名。")
        String outputColumnName
) { }

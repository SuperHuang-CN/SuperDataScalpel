package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonClassDescription("多变量格网中的一个独立变量；每项选择来源表和 Geometry，可使用受控筛选，并生成一个结果字段。")
public record SpatialMultiVariableGridVariable(
        @JsonPropertyDescription("节点内稳定且唯一的 UUID；排序或编辑时保持不变。")
        String variableId,
        @JsonPropertyDescription("必填的有界上游 Canvas 逻辑表名；多个变量可以引用同一表。")
        String sourceTableName,
        @JsonPropertyDescription("必填的投影 XY Point、Line 或 Polygon 家族 Geometry 字段。")
        String geometryColumnName,
        @JsonPropertyDescription("必填变量类型。")
        SpatialMultiVariableGridVariableKind kind,
        @JsonPropertyDescription("ATTRIBUTE_OF_NEAREST 时必填的非 Geometry 标量字段；其他类型作为非活动草稿保留。")
        String attributeColumnName,
        @JsonPropertyDescription("ATTRIBUTE_SUMMARY_OF_RELATED 时必填的统计类型；其他类型作为非活动草稿保留。")
        SpatialMultiVariableGridStatisticKind statisticKind,
        @JsonPropertyDescription("关联汇总除 COUNT 外必填的标量字段；ANY 要求 STRING，其余要求数值类型。")
        String statisticColumnName,
        @JsonPropertyDescription("最近距离和最近属性必填的有限正数搜索距离；关联汇总可为空，为空时按要素与格网相交，非空时按格网中心搜索。")
        Double searchDistance,
        @JsonPropertyDescription("配置 searchDistance 时必填；距离结果也使用该单位输出。")
        SpatialDistanceUnit searchDistanceUnit,
        @JsonPropertyDescription("可选的受控字段条件树，只影响当前变量的候选要素，不改变共同分析范围。")
        CanvasFilterCondition filter,
        @JsonPropertyDescription("必填且在结果表中唯一的变量输出字段名。")
        String outputColumnName
) {
}

package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("用已有多变量 Polygon 格网的显式属性丰富一张 Point 表，并生成新的 Point 结果表。")
public record SpatialEnrichFromGridConfiguration(
        @JsonPropertyDescription("需要丰富的有界 Point 逻辑表名。")
        String pointTableName,
        @JsonPropertyDescription("Point 表中的 XY Point Geometry 字段名。")
        String pointGeometryColumnName,
        @JsonPropertyDescription("已有多变量格网逻辑表名；必须是有界 XY Polygon 或 MultiPolygon。")
        String gridTableName,
        @JsonPropertyDescription("格网表中的 XY Polygon Geometry 字段名。")
        String gridGeometryColumnName,
        @JsonPropertyDescription("格网表中的稳定格网唯一标识字段；边界点命中多个格网时按该字段的字符串顺序选择第一个。")
        String gridIdColumnName,
        @JsonPropertyDescription("要从格网回填的显式字段列表；至少一项，按配置顺序追加到结果。")
        List<SpatialEnrichFromGridField> enrichFields,
        @JsonPropertyDescription("新生成的 Canvas Point 逻辑表名；来源 Point 表和格网表继续保留。")
        String outputTableName
) {
    public SpatialEnrichFromGridConfiguration {
        enrichFields = enrichFields == null ? null : List.copyOf(enrichFields);
    }
}

package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("有界批处理空间聚合配置。按普通标量字段分组，对每组执行 1 至 32 个固定 Geometry 聚合；Canvas 4.53 可为单 UNION 显式启用 Dissolve 的计数、标量统计和 multipart/singlepart 输出，Canvas 4.61 可按无字段空间连通组融合。结果无顺序保证，事件时间和 Watermark 被清空。")

public record SpatialAggregateConfiguration(
        @JsonPropertyDescription("要聚合的上游 Canvas 逻辑表名，必须精确引用一张 BOUNDED 表；其他上游逻辑表继续传播但不参与聚合。")
        String sourceTableName,
        @JsonPropertyDescription("追加聚合结果使用的 Canvas 逻辑表名，必须与当前所有上游表名不同；来源表和其他上游表仍然保留。")
        String outputTableName,
        @JsonPropertyDescription("有序分组字段数组，必须存在但可以为空。字段不能重复且不能是 GEOMETRY；空数组把全部记录作为一个全局组。输出先按此顺序保留分组字段，并清空其物理来源、默认值和生成标记。")
        List<String> groupByColumns,
        @JsonPropertyDescription("有序空间聚合项数组，必须包含 1 至 32 项且不能含 null。各项可使用不同 Geometry 字段和 CRS，独立继承自身来源 CRS/维度；输出按数组顺序位于分组字段之后。")
        List<SpatialAggregation> aggregations,
        @JsonPropertyDescription("Canvas 4.53 起的可选 Dissolve 输出选项。null 保持旧空间聚合行为；非 null 时要求恰好一个 UNION 聚合，并在结果中增加来源要素计数和可选标量统计。4.61 可显式选择无字段空间连通组。")
        SpatialAggregateDissolveOptions dissolve
) {
    public static final int MAX_AGGREGATIONS = 32;

    public SpatialAggregateConfiguration {
        groupByColumns = groupByColumns == null ? null : List.copyOf(groupByColumns);
        aggregations = aggregations == null ? null : List.copyOf(aggregations);
    }

    public SpatialAggregateConfiguration(
            String sourceTableName,
            String outputTableName,
            List<String> groupByColumns,
            List<SpatialAggregation> aggregations
    ) {
        this(sourceTableName, outputTableName, groupByColumns, aggregations, null);
    }
}

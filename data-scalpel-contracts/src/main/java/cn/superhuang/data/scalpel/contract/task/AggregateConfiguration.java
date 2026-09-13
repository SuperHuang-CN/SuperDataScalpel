package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("批处理分组聚合配置；读取一张 BOUNDED 上游逻辑表，按零到多个字段分组并生成一张新的 BOUNDED 结果表。输出先按 groupByColumns 顺序排列分组字段，再按 aggregations 顺序排列指标字段；结果不继承物理来源、事件时间或 Watermark。")

public record AggregateConfiguration(
        @JsonPropertyDescription("要聚合的上游 Canvas 逻辑表名；必须引用进入本节点前已经存在的 BOUNDED 表。")
        String sourceTableName,
        @JsonPropertyDescription("聚合结果的 Canvas 逻辑表名；必须与进入节点时已有的所有表名不同，成功后作为新表加入表集合。")
        String outputTableName,
        @JsonPropertyDescription("按配置顺序参与分组并出现在结果最前方的来源字段；使用空数组表示把整张输入表作为一个组。字段必须存在、互不重复，且不能是 Geometry。")
        List<String> groupByColumns,
        @JsonPropertyDescription("按配置顺序生成指标字段的聚合项；至少一项。各输出字段名必须互不重复，也不能与任何分组字段同名。")
        List<AggregateItem> aggregations
) {
    public AggregateConfiguration {
        groupByColumns = groupByColumns == null ? null : List.copyOf(groupByColumns);
        aggregations = aggregations == null ? null : List.copyOf(aggregations);
    }
}

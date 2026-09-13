package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("按字段名纵向合并逻辑表的配置。缺失/null mergingTables 保持旧版严格同 Schema Union；Canvas 4.62 可以基准输入层加多张合并层的方式执行 Match/Rename/Remove、缺失字段补 NULL 的 Merge Layers。")

public record UnionConfiguration(
        @JsonPropertyDescription("按顺序参与 Union 的不同上游逻辑表名；至少两项且不能重复。第一项决定输出字段顺序，其余表按字段名重排后合并。")
        List<String> inputTableNames,
        @JsonPropertyDescription("Union 结果的 Canvas 逻辑表名；必须与进入节点时已有的所有表名不同，成功后作为新表加入表集合。")
        String outputTableName,
        @JsonPropertyDescription("必填合并模式。ALL 保留所有输入行及重复行；DISTINCT 对完整合并结果按全部字段去重。包含 Geometry 字段时不能使用 DISTINCT，无界输入也不能使用 DISTINCT。")
        UnionMode mode,
        @JsonPropertyDescription("Canvas 4.62 起的可选合并层字段处理。null 保持旧版字段集必须完全一致；空数组启用默认同名 Match/原名追加，并为缺失字段补 NULL；非空数组只需列出有自定义规则的合并层。")
        List<UnionMergeTable> mergingTables
) {
    public UnionConfiguration {
        inputTableNames = inputTableNames == null ? null : List.copyOf(inputTableNames);
        mergingTables = mergingTables == null ? null : List.copyOf(mergingTables);
    }

    public UnionConfiguration(
            List<String> inputTableNames,
            String outputTableName,
            UnionMode mode
    ) {
        this(inputTableNames, outputTableName, mode, null);
    }
}

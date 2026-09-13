package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("仅支持 BATCH 和 BOUNDED 来源的 Top N 配置；对一个或多个不同上游表分别按显式排序选择全局或各分组前 N 名。排序只决定保留记录，不承诺下游物理行顺序；输出清除事件时间与 Watermark。")
public record TopNConfiguration(
        @JsonPropertyDescription("独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。")
        List<TopNOperation> operations
) {
    public TopNConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }
    public TopNConfiguration(String sourceTableName, String outputTableName, List<String> partitionByColumns,
                             List<SortField> orderBy, int limit, TopNTieStrategy tieStrategy) {
        this(List.of(new TopNOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), partitionByColumns, orderBy, limit, tieStrategy)));
    }
    private TopNOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<String> partitionByColumns() { return single() == null ? null : single().partitionByColumns(); }
    public List<SortField> orderBy() { return single() == null ? null : single().orderBy(); }
    public int limit() { return single() == null ? 0 : single().limit(); }
    public TopNTieStrategy tieStrategy() { return single() == null ? null : single().tieStrategy(); }
}

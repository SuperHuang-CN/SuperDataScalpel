package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("仅支持 BATCH 的有界表去重配置；对一个或多个不同上游表分别按全行或业务键识别重复记录，并按 ANY、FIRST 或 LAST 选择保留行。输出继承字段和来源，固定为 BOUNDED 并清除事件时间与 Watermark。")
public record DeduplicateConfiguration(
        @JsonPropertyDescription("独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。")
        List<DeduplicateOperation> operations
) {
    public DeduplicateConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }

    public DeduplicateConfiguration(String sourceTableName, String outputTableName, List<String> keyColumns,
                                    DeduplicateKeepStrategy keepStrategy, List<SortField> orderBy) {
        this(List.of(new DeduplicateOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), keyColumns, keepStrategy, orderBy)));
    }
    private DeduplicateOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<String> keyColumns() { return single() == null ? null : single().keyColumns(); }
    public DeduplicateKeepStrategy keepStrategy() { return single() == null ? null : single().keepStrategy(); }
    public List<SortField> orderBy() { return single() == null ? null : single().orderBy(); }
}

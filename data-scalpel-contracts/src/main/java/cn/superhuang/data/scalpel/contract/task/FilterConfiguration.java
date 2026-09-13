package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("支持 BATCH 和 STREAMING 的行筛选配置；对一个或多个不同上游表分别使用结构化条件树或受控 Spark SQL 布尔谓词过滤记录。输出完整继承来源字段、Origin、有界性、事件时间和 Watermark，任一操作校验失败会使整个节点无效。")
public record FilterConfiguration(
        @JsonPropertyDescription("独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。")
        List<FilterOperation> operations
) {
    public FilterConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }

    public FilterConfiguration(String sourceTableName, String outputTableName, CanvasFilterCondition condition) {
        this(List.of(new FilterOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), condition)));
    }

    private FilterOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public FilterConditionMode mode() { return single() == null ? FilterConditionMode.STRUCTURED : single().mode(); }
    public CanvasFilterCondition condition() { return single() == null ? null : single().condition(); }
    public String sqlExpression() { return single() == null ? "" : single().sqlExpression(); }
}

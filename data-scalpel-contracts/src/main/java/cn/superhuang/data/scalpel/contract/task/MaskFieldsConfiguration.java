package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("支持 BATCH 和 STREAMING 的无状态字段脱敏配置；对一个或多个不同上游表分别用节点内嵌 definition 原位替换选定字段。全局规则只是在设计时复制的模板，保存、发布、编译和运行均不查询或同步全局规则。")
public record MaskFieldsConfiguration(
        @JsonPropertyDescription("独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。")
        List<MaskFieldsOperation> operations
) {
    public MaskFieldsConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }
    public MaskFieldsConfiguration(String sourceTableName, String outputTableName, List<MaskFieldRule> fieldRules) {
        this(List.of(new MaskFieldsOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), fieldRules)));
    }
    private MaskFieldsOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<MaskFieldRule> fieldRules() { return single() == null ? null : single().fieldRules(); }
}

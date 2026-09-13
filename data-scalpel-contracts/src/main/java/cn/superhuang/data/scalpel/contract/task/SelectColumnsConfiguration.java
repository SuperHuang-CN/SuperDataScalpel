package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("支持 BATCH 和 STREAMING 的字段投影配置；对一个或多个不同上游表分别按声明顺序裁剪并重排字段。字段元数据和来源有界性继承原表；若裁掉事件时间字段，同时清除事件时间及 Watermark 标记。")
public record SelectColumnsConfiguration(
        @JsonPropertyDescription("独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。")
        List<SelectColumnsOperation> operations
) {
    public SelectColumnsConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }
    public SelectColumnsConfiguration(String sourceTableName, String outputTableName, List<String> columns) {
        this(List.of(new SelectColumnsOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), columns)));
    }
    private SelectColumnsOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<String> columns() { return single() == null ? null : single().columns(); }
}

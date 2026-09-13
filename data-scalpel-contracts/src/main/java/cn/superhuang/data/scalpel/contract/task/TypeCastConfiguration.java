package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("支持 BATCH 和 STREAMING 的字段类型转换配置；对一个或多个不同上游表分别在原字段位置执行显式 Spark cast、容错 try_cast 或受控日期时间转换。未配置字段完整继承，转换字段清除物理默认值、生成属性和注释；流任务禁止转换事件时间字段。")
public record TypeCastConfiguration(
        @JsonPropertyDescription("独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。")
        List<TypeCastOperation> operations
) {
    public TypeCastConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }
    public TypeCastConfiguration(String sourceTableName, String outputTableName, List<ColumnTypeCast> casts) {
        this(List.of(new TypeCastOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), casts)));
    }
    private TypeCastOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<ColumnTypeCast> casts() { return single() == null ? null : single().casts(); }
}

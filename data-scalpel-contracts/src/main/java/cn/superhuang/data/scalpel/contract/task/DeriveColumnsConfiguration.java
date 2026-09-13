package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

@JsonClassDescription("支持 BATCH 和 STREAMING 的派生字段配置；每张已选表执行 globalDerivations 加该表局部 derivations，并通过一次最终投影覆盖原字段或追加新字段。所有表达式只读取该表进入节点时的原始 Schema，不能引用本节点刚派生的字段；任一表不兼容会使整个节点无效。")
public record DeriveColumnsConfiguration(
        @JsonPropertyDescription("自动应用到 operations 中每一张来源表的有序派生规则；NULL 规范化为空数组。每条规则必须对所有已选表有效，并且目标字段不能与任一表自己的 derivations 冲突。")
        List<ColumnDerivation> globalDerivations,
        @JsonPropertyDescription("独立处理不同来源表的操作数组，至少一项；operationId 和 sourceTableName 在节点内都必须唯一。各项只能读取进入本节点时已有的上游表，不能在同一节点内继续读取其他操作刚生成的结果。每表有效规则总数最多 100。")
        List<DeriveColumnsOperation> operations
) {
    public DeriveColumnsConfiguration {
        globalDerivations = globalDerivations == null ? List.of() : List.copyOf(globalDerivations);
        operations = operations == null ? null : List.copyOf(operations);
    }

    public DeriveColumnsConfiguration(List<DeriveColumnsOperation> operations) {
        this(List.of(), operations);
    }

    public DeriveColumnsConfiguration(String sourceTableName, String outputTableName, List<ColumnDerivation> derivations) {
        this(List.of(), List.of(new DeriveColumnsOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), derivations)));
    }
    private DeriveColumnsOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<ColumnDerivation> derivations() { return single() == null ? null : single().derivations(); }
}

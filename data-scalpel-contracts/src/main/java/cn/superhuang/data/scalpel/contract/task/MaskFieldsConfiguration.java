package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record MaskFieldsConfiguration(List<MaskFieldsOperation> operations) {
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

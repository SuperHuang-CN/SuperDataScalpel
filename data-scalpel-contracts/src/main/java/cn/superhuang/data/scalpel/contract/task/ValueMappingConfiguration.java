package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record ValueMappingConfiguration(List<ValueMappingOperation> operations) {
    public ValueMappingConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }
    public ValueMappingConfiguration(String sourceTableName, String outputTableName, List<ValueMappingRule> rules) {
        this(List.of(new ValueMappingOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), rules)));
    }
    private ValueMappingOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<ValueMappingRule> rules() { return single() == null ? null : single().rules(); }
}

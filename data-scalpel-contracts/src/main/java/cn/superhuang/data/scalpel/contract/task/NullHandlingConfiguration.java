package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record NullHandlingConfiguration(List<NullHandlingOperation> operations) {
    public NullHandlingConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }
    public NullHandlingConfiguration(String sourceTableName, String outputTableName, List<NullHandlingRule> rules) {
        this(List.of(new NullHandlingOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), rules)));
    }
    private NullHandlingOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<NullHandlingRule> rules() { return single() == null ? null : single().rules(); }
}

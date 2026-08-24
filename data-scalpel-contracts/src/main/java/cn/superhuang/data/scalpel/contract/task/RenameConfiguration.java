package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record RenameConfiguration(List<RenameOperation> operations) {
    public RenameConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }
    public RenameConfiguration(String sourceTableName, String outputTableName, List<RenameColumnMapping> columnMappings) {
        this(List.of(new RenameOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.ReplaceSource(outputTableName), columnMappings)));
    }
    private RenameOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<RenameColumnMapping> columnMappings() { return single() == null ? null : single().columnMappings(); }
}

package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TypeCastConfiguration(List<TypeCastOperation> operations) {
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

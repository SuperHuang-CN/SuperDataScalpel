package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record SelectColumnsConfiguration(List<SelectColumnsOperation> operations) {
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

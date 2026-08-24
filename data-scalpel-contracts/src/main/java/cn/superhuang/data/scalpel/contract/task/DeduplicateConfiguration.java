package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record DeduplicateConfiguration(List<DeduplicateOperation> operations) {
    public DeduplicateConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }

    public DeduplicateConfiguration(String sourceTableName, String outputTableName, List<String> keyColumns,
                                    DeduplicateKeepStrategy keepStrategy, List<SortField> orderBy) {
        this(List.of(new DeduplicateOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), keyColumns, keepStrategy, orderBy)));
    }
    private DeduplicateOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<String> keyColumns() { return single() == null ? null : single().keyColumns(); }
    public DeduplicateKeepStrategy keepStrategy() { return single() == null ? null : single().keepStrategy(); }
    public List<SortField> orderBy() { return single() == null ? null : single().orderBy(); }
}

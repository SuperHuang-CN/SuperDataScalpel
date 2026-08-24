package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TopNConfiguration(List<TopNOperation> operations) {
    public TopNConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }
    public TopNConfiguration(String sourceTableName, String outputTableName, List<String> partitionByColumns,
                             List<SortField> orderBy, int limit, TopNTieStrategy tieStrategy) {
        this(List.of(new TopNOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), partitionByColumns, orderBy, limit, tieStrategy)));
    }
    private TopNOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public List<String> partitionByColumns() { return single() == null ? null : single().partitionByColumns(); }
    public List<SortField> orderBy() { return single() == null ? null : single().orderBy(); }
    public int limit() { return single() == null ? 0 : single().limit(); }
    public TopNTieStrategy tieStrategy() { return single() == null ? null : single().tieStrategy(); }
}

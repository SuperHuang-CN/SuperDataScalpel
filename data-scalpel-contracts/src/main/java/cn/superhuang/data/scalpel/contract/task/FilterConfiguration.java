package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record FilterConfiguration(List<FilterOperation> operations) {
    public FilterConfiguration {
        operations = operations == null ? null : List.copyOf(operations);
    }

    public FilterConfiguration(String sourceTableName, String outputTableName, CanvasFilterCondition condition) {
        this(List.of(new FilterOperation(ProcessorOperation.LEGACY_OPERATION_ID, sourceTableName,
                new ProcessorOutput.CreateNewTable(outputTableName), condition)));
    }

    private FilterOperation single() { return operations == null || operations.size() != 1 ? null : operations.getFirst(); }
    public String sourceTableName() { return single() == null ? null : single().sourceTableName(); }
    public String outputTableName() { return single() == null || single().output() == null ? null : single().output().outputTableName(); }
    public FilterConditionMode mode() { return single() == null ? FilterConditionMode.STRUCTURED : single().mode(); }
    public CanvasFilterCondition condition() { return single() == null ? null : single().condition(); }
    public String sqlExpression() { return single() == null ? "" : single().sqlExpression(); }
}

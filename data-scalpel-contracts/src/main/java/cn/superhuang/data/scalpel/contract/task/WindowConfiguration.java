package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record WindowConfiguration(
        String sourceTableName,
        String outputTableName,
        List<String> partitionByColumns,
        List<SortField> orderBy,
        List<WindowFunctionItem> functions
) {
    public WindowConfiguration {
        partitionByColumns = partitionByColumns == null ? null : List.copyOf(partitionByColumns);
        orderBy = orderBy == null ? null : List.copyOf(orderBy);
        functions = functions == null ? null : List.copyOf(functions);
    }
}

package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record DropNullRowsRule(
        List<String> columnNames,
        NullMatchMode matchMode
) implements NullHandlingRule {
    public DropNullRowsRule {
        columnNames = columnNames == null ? null : List.copyOf(columnNames);
    }
}

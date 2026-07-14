package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

public record TableList(List<TableSummary> tables, boolean truncated) {
    public TableList {
        tables = List.copyOf(tables);
    }
}

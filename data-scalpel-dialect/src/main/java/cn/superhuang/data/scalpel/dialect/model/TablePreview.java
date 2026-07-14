package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

public record TablePreview(
        TableIdentifier table,
        List<PreviewColumn> columns,
        List<List<Object>> rows,
        int limit,
        boolean truncated
) {
    public TablePreview {
        columns = List.copyOf(columns);
        rows = rows.stream().map(List::copyOf).toList();
    }
}

package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

public record IndexMetadata(
        String name,
        boolean unique,
        List<String> columns,
        boolean usableAsUniqueKey
) {
    public IndexMetadata {
        columns = List.copyOf(columns);
    }

    public IndexMetadata(String name, boolean unique, List<String> columns) {
        this(name, unique, columns, unique && columns != null && !columns.isEmpty());
    }
}

package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

public record IndexMetadata(String name, boolean unique, List<String> columns) {
    public IndexMetadata {
        columns = List.copyOf(columns);
    }
}

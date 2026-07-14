package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

public record PrimaryKeyMetadata(String name, List<String> columns) {
    public PrimaryKeyMetadata {
        columns = List.copyOf(columns);
    }
}

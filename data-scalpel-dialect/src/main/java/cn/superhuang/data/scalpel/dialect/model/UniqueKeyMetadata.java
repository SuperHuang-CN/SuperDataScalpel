package cn.superhuang.data.scalpel.dialect.model;

import java.util.List;

public record UniqueKeyMetadata(String name, UniqueKeyKind kind, List<String> columns) {
    public UniqueKeyMetadata {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}

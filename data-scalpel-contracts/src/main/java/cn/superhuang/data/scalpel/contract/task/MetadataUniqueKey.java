package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record MetadataUniqueKey(
        String name,
        MetadataUniqueKeyType type,
        List<String> columns
) {
    public MetadataUniqueKey {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}

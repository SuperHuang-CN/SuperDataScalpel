package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record SelectColumnsConfiguration(
        String sourceTableName,
        String outputTableName,
        List<String> columns
) {
    public SelectColumnsConfiguration {
        columns = columns == null ? null : List.copyOf(columns);
    }
}

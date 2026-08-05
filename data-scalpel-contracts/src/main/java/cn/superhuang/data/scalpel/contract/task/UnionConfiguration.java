package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record UnionConfiguration(
        List<String> inputTableNames,
        String outputTableName,
        UnionMode mode
) {
    public UnionConfiguration {
        inputTableNames = inputTableNames == null ? null : List.copyOf(inputTableNames);
    }
}

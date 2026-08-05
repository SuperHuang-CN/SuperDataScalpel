package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TypeCastConfiguration(
        String sourceTableName,
        String outputTableName,
        List<ColumnTypeCast> casts
) {
    public TypeCastConfiguration {
        casts = casts == null ? null : List.copyOf(casts);
    }
}

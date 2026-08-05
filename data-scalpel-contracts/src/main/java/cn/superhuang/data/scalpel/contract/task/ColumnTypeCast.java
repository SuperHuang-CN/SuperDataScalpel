package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

public record ColumnTypeCast(
        String columnName,
        PlatformTypeDefinition targetType,
        CastFailureStrategy failureStrategy
) {
}

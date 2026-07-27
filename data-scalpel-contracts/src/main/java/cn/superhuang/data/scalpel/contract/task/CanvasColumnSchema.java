package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

public record CanvasColumnSchema(
        String name,
        PlatformDataType fieldType,
        Integer length,
        Integer precision,
        Integer scale,
        boolean nullable,
        String defaultValue,
        boolean autoIncrement,
        boolean generated,
        String comment
) {
}

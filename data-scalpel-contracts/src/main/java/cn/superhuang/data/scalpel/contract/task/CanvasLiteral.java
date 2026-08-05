package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

public record CanvasLiteral(
        PlatformDataType dataType,
        String value
) {
}

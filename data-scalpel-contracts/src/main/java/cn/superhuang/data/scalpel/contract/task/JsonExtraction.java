package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;

public record JsonExtraction(
        String jsonPath,
        String outputColumnName,
        PlatformTypeDefinition targetType
) {
}

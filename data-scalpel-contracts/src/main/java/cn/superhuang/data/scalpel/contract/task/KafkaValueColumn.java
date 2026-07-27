package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;

public record KafkaValueColumn(
        String name,
        PlatformDataType fieldType,
        Integer length,
        Integer precision,
        Integer scale,
        boolean nullable,
        String comment
) {
}

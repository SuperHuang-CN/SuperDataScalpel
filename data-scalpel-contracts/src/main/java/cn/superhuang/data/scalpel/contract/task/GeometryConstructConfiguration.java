package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.GeometryTypeDefinition;

public record GeometryConstructConfiguration(
        String sourceTableName,
        String outputTableName,
        String outputColumnName,
        GeometryConstructSource source,
        GeometryTypeDefinition targetGeometry
) {
}

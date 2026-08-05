package cn.superhuang.data.scalpel.contract.task;

import cn.superhuang.data.scalpel.contract.type.CrsReference;

public record SpatialTransformConfiguration(
        String sourceTableName,
        String outputTableName,
        String geometryColumnName,
        CrsReference targetCrs
) {
}

package cn.superhuang.data.scalpel.contract.task;

public record GeometryExplodeConfiguration(
        String sourceTableName,
        String outputTableName,
        String geometryColumnName,
        String outputColumnName,
        String partIndexColumnName
) {
}

package cn.superhuang.data.scalpel.contract.task;

public record GeometryRepairConfiguration(
        String sourceTableName,
        String outputTableName,
        String geometryColumnName,
        String outputColumnName
) {
}

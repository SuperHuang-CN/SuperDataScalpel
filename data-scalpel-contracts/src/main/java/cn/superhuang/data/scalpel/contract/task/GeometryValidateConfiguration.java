package cn.superhuang.data.scalpel.contract.task;

public record GeometryValidateConfiguration(
        String sourceTableName,
        String outputTableName,
        String geometryColumnName,
        String validColumnName,
        String reasonColumnName
) {
}

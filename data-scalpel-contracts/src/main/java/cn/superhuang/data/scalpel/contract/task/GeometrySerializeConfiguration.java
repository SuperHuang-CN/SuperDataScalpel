package cn.superhuang.data.scalpel.contract.task;

public record GeometrySerializeConfiguration(
        String sourceTableName,
        String outputTableName,
        String geometryColumnName,
        String outputColumnName,
        GeometrySerializationFormat format
) {
}

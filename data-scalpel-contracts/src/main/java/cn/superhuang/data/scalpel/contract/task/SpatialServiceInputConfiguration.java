package cn.superhuang.data.scalpel.contract.task;

public record SpatialServiceInputConfiguration(
        String dataSourceId,
        String resourceId,
        String outputTableName
) {
}

package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record SpatialServiceInputConfiguration(
        String dataSourceId,
        List<SpatialServiceInputResourceSelection> resources
) {
    public SpatialServiceInputConfiguration {
        resources = resources == null ? List.of() : List.copyOf(resources);
    }
}

package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record SpatialMeasureConfiguration(
        String sourceTableName,
        String outputTableName,
        List<SpatialMeasurement> measurements
) {
    public static final int MAX_MEASUREMENTS = 32;

    public SpatialMeasureConfiguration {
        measurements = measurements == null ? null : List.copyOf(measurements);
    }
}

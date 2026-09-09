package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = TrackMotionMetric.Distance.class, name = "DISTANCE"),
        @JsonSubTypes.Type(value = TrackMotionMetric.ElevationChange.class, name = "ELEVATION_CHANGE"),
        @JsonSubTypes.Type(value = TrackMotionMetric.Duration.class, name = "DURATION"),
        @JsonSubTypes.Type(value = TrackMotionMetric.Speed.class, name = "SPEED"),
        @JsonSubTypes.Type(value = TrackMotionMetric.Acceleration.class, name = "ACCELERATION"),
        @JsonSubTypes.Type(value = TrackMotionMetric.Bearing.class, name = "BEARING"),
        @JsonSubTypes.Type(value = TrackMotionMetric.Slope.class, name = "SLOPE"),
        @JsonSubTypes.Type(value = TrackMotionMetric.Idle.class, name = "IDLE")
})
public sealed interface TrackMotionMetric permits
        TrackMotionMetric.Distance, TrackMotionMetric.ElevationChange,
        TrackMotionMetric.Duration, TrackMotionMetric.Speed,
        TrackMotionMetric.Acceleration, TrackMotionMetric.Bearing,
        TrackMotionMetric.Slope, TrackMotionMetric.Idle {

    String metricId();

    String outputColumnName();

    record Distance(String metricId, String outputColumnName, SpatialDistanceUnit outputUnit)
            implements TrackMotionMetric {
    }

    record ElevationChange(String metricId, String outputColumnName, SpatialDistanceUnit outputUnit)
            implements TrackMotionMetric {
    }

    record Duration(String metricId, String outputColumnName, SpatialDurationUnit outputUnit)
            implements TrackMotionMetric {
    }

    record Speed(String metricId, String outputColumnName, SpatialSpeedUnit outputUnit)
            implements TrackMotionMetric {
    }

    record Acceleration(String metricId, String outputColumnName, SpatialAccelerationUnit outputUnit)
            implements TrackMotionMetric {
    }

    record Bearing(String metricId, String outputColumnName, String outputUnit)
            implements TrackMotionMetric {
    }

    record Slope(String metricId, String outputColumnName, String outputUnit)
            implements TrackMotionMetric {
    }

    record Idle(String metricId, String outputColumnName, Void outputUnit)
            implements TrackMotionMetric {
    }
}

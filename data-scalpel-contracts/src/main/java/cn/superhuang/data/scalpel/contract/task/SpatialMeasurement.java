package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(value = SpatialMeasurement.Area.class, name = "AREA"),
        @JsonSubTypes.Type(value = SpatialMeasurement.Length.class, name = "LENGTH"),
        @JsonSubTypes.Type(value = SpatialMeasurement.Perimeter.class, name = "PERIMETER"),
        @JsonSubTypes.Type(value = SpatialMeasurement.Distance.class, name = "DISTANCE"),
        @JsonSubTypes.Type(value = SpatialMeasurement.X.class, name = "X"),
        @JsonSubTypes.Type(value = SpatialMeasurement.Y.class, name = "Y")
})
public sealed interface SpatialMeasurement permits
        SpatialMeasurement.Area,
        SpatialMeasurement.Length,
        SpatialMeasurement.Perimeter,
        SpatialMeasurement.Distance,
        SpatialMeasurement.X,
        SpatialMeasurement.Y {

    String outputColumnName();

    record Area(
            String geometryColumnName,
            SpatialMeasureMode mode,
            String outputColumnName
    ) implements SpatialMeasurement {
    }

    record Length(
            String geometryColumnName,
            SpatialMeasureMode mode,
            String outputColumnName
    ) implements SpatialMeasurement {
    }

    record Perimeter(
            String geometryColumnName,
            SpatialMeasureMode mode,
            String outputColumnName
    ) implements SpatialMeasurement {
    }

    record Distance(
            String leftGeometryColumnName,
            String rightGeometryColumnName,
            SpatialMeasureMode mode,
            String outputColumnName
    ) implements SpatialMeasurement {
    }

    record X(
            String geometryColumnName,
            String outputColumnName
    ) implements SpatialMeasurement {
    }

    record Y(
            String geometryColumnName,
            String outputColumnName
    ) implements SpatialMeasurement {
    }
}

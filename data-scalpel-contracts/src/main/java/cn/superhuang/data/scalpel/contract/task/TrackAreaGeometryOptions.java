package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

/** Optional area reconstruction; inactive buffer branches remain in the saved draft. */
public record TrackAreaGeometryOptions(
        Boolean enabled,
        TrackBufferMode bufferMode,
        String bufferField,
        String bufferExpression,
        SpatialDistanceUnit bufferUnit,
        List<TrackBufferWindowBinding> windowBindings,
        TrackGeodesicAreaOptions geodesicBoundary
) {
    public TrackAreaGeometryOptions {
        windowBindings = windowBindings == null ? List.of() : List.copyOf(windowBindings);
    }
    public TrackAreaGeometryOptions(Boolean enabled, TrackBufferMode bufferMode, String bufferField,
            String bufferExpression, SpatialDistanceUnit bufferUnit) {
        this(enabled, bufferMode, bufferField, bufferExpression, bufferUnit, List.of());
    }
    public TrackAreaGeometryOptions(Boolean enabled, TrackBufferMode bufferMode, String bufferField,
            String bufferExpression, SpatialDistanceUnit bufferUnit, List<TrackBufferWindowBinding> windowBindings) {
        this(enabled, bufferMode, bufferField, bufferExpression, bufferUnit, windowBindings, null);
    }
    public boolean active() { return !Boolean.FALSE.equals(enabled); }
}

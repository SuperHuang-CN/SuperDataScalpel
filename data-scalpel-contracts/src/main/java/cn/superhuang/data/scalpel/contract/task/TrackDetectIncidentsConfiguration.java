package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TrackDetectIncidentsConfiguration(
        String sourceTableName,
        String pointGeometryColumnName,
        List<String> trackIdColumns,
        String timeColumnName,
        SpatialDistanceMethod distanceMethod,
        TrackBoundaryConfiguration boundaries,
        CanvasFilterCondition startCondition,
        CanvasFilterCondition endCondition,
        TrackIncidentResultMode resultMode,
        String outputTableName,
        String incidentIdColumnName,
        String incidentFlagColumnName,
        String incidentStartTimeColumnName,
        String incidentEndTimeColumnName,
        String incidentDurationColumnName,
        SpatialDurationUnit incidentDurationUnit,
        TrackIncidentSemantics incidentSemantics,
        String incidentStatusColumnName,
        List<String> orderByColumns,
        List<TrackIncidentWindow> conditionWindows
) {
    public static final int MAX_TRACK_ID_COLUMNS = 8;

    public TrackDetectIncidentsConfiguration {
        trackIdColumns = trackIdColumns == null ? null : List.copyOf(trackIdColumns);
        orderByColumns = orderByColumns == null ? List.of() : List.copyOf(orderByColumns);
        conditionWindows = conditionWindows == null ? List.of() : List.copyOf(conditionWindows);
    }

    public TrackIncidentSemantics effectiveIncidentSemantics() {
        return incidentSemantics == null ? TrackIncidentSemantics.LEGACY : incidentSemantics;
    }

    public boolean usesLifecycleOptions() {
        return effectiveIncidentSemantics() != TrackIncidentSemantics.LEGACY
                || incidentStatusColumnName != null || !orderByColumns.isEmpty();
    }

    public TrackDetectIncidentsConfiguration(
            String sourceTableName, String pointGeometryColumnName, List<String> trackIdColumns,
            String timeColumnName, SpatialDistanceMethod distanceMethod, TrackBoundaryConfiguration boundaries,
            CanvasFilterCondition startCondition, CanvasFilterCondition endCondition, TrackIncidentResultMode resultMode,
            String outputTableName, String incidentIdColumnName, String incidentFlagColumnName,
            String incidentStartTimeColumnName, String incidentEndTimeColumnName, String incidentDurationColumnName,
            SpatialDurationUnit incidentDurationUnit, TrackIncidentSemantics incidentSemantics,
            String incidentStatusColumnName, List<String> orderByColumns
    ) {
        this(sourceTableName, pointGeometryColumnName, trackIdColumns, timeColumnName, distanceMethod, boundaries,
                startCondition, endCondition, resultMode, outputTableName, incidentIdColumnName, incidentFlagColumnName,
                incidentStartTimeColumnName, incidentEndTimeColumnName, incidentDurationColumnName, incidentDurationUnit,
                incidentSemantics, incidentStatusColumnName, orderByColumns, List.of());
    }

    public TrackDetectIncidentsConfiguration(
            String sourceTableName, String pointGeometryColumnName, List<String> trackIdColumns,
            String timeColumnName, SpatialDistanceMethod distanceMethod, TrackBoundaryConfiguration boundaries,
            CanvasFilterCondition startCondition, CanvasFilterCondition endCondition, TrackIncidentResultMode resultMode,
            String outputTableName, String incidentIdColumnName, String incidentFlagColumnName,
            String incidentStartTimeColumnName, String incidentEndTimeColumnName, String incidentDurationColumnName,
            SpatialDurationUnit incidentDurationUnit
    ) {
        this(sourceTableName, pointGeometryColumnName, trackIdColumns, timeColumnName, distanceMethod, boundaries,
                startCondition, endCondition, resultMode, outputTableName, incidentIdColumnName, incidentFlagColumnName,
                incidentStartTimeColumnName, incidentEndTimeColumnName, incidentDurationColumnName, incidentDurationUnit,
                null, null, List.of());
    }
}

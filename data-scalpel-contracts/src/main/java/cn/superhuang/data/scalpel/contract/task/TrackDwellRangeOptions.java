package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TrackDwellRangeOptions(
        TrackDwellResultMode resultMode,
        List<String> orderByColumns,
        SpatialDurationUnit durationUnit,
        String meanDistanceColumnName,
        SpatialDistanceUnit meanDistanceUnit,
        String dwellFlagColumnName
) {
    public TrackDwellRangeOptions {
        orderByColumns = orderByColumns == null ? List.of() : List.copyOf(orderByColumns);
    }
}

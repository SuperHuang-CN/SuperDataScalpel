package cn.superhuang.data.scalpel.contract.task;

public record SpatialTemporalSlicing(
        String timeColumnName,
        long interval,
        SpatialDurationUnit intervalUnit,
        Long repeatInterval,
        SpatialDurationUnit repeatIntervalUnit,
        String referenceTime,
        String timeZone,
        String windowStartColumnName,
        String windowEndColumnName,
        SpatialCalendarWindowOptions calendar
) {
    public SpatialTemporalSlicing(String timeColumnName, long interval, SpatialDurationUnit intervalUnit,
                                 Long repeatInterval, SpatialDurationUnit repeatIntervalUnit, String referenceTime,
                                 String timeZone, String windowStartColumnName, String windowEndColumnName) {
        this(timeColumnName, interval, intervalUnit, repeatInterval, repeatIntervalUnit, referenceTime, timeZone,
                windowStartColumnName, windowEndColumnName, null);
    }

    public boolean usesCalendar() {
        return calendar != null && calendar.mode() == SpatialCalendarWindowOptions.Mode.CALENDAR;
    }
}

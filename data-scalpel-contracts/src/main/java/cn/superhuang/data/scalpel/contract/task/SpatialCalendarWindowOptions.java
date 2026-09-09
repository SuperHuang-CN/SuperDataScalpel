package cn.superhuang.data.scalpel.contract.task;

/** Explicit time semantics; existing fixed-duration units remain available as an inactive draft. */
public record SpatialCalendarWindowOptions(Mode mode, Unit intervalUnit, Unit repeatIntervalUnit) {
    public enum Mode { FIXED_DURATION, CALENDAR }
    public enum Unit { MILLISECONDS, SECONDS, MINUTES, HOURS, DAYS, WEEKS, MONTHS, YEARS }
}

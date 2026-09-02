package cn.superhuang.data.scalpel.contract.task;

/** Explicit formatting used when a DATE or timestamp value is converted to STRING. */
public record TemporalStringFormatOptions(
        String pattern,
        String targetTimeZone
) {
}

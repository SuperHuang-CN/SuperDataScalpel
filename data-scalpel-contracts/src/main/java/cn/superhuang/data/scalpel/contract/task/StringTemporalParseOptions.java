package cn.superhuang.data.scalpel.contract.task;

/** Explicit parsing settings for STRING to DATE or TIMESTAMP casts. */
public record StringTemporalParseOptions(
        String pattern,
        StringTimestampZoneMode zoneMode,
        String sourceTimeZone
) {
}

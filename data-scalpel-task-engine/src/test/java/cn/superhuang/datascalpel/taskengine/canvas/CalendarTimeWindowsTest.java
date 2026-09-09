package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import org.apache.spark.sql.Row;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static cn.superhuang.data.scalpel.contract.task.SpatialCalendarWindowOptions.Unit.*;
import static org.junit.jupiter.api.Assertions.*;

class CalendarTimeWindowsTest {
    @Test void fixedWeeksAreSevenElapsedDaysAndCalendarWeeksKeepDstSemantics() {
        var issues = new Issues();
        var slicing = new SpatialTemporalSlicing("time", 1, SpatialDurationUnit.WEEKS, 2L, SpatialDurationUnit.WEEKS,
                "2024-03-04T00:00:00-05:00", "America/New_York", "start", "end");
        var fixed = SpatialTemporalSupport.validateAndResolve(slicing, "time", issues);
        assertFalse(issues.hasErrors()); assertNotNull(fixed); assertNull(fixed.calendar());
        assertEquals(604_800_000_000L, fixed.windowMicros()); assertEquals(1_209_600_000_000L, fixed.slideMicros());
        assertEquals(604_800_000d, TrackNodeSupport.durationMillis(1, SpatialDurationUnit.WEEKS));
        assertEquals(TrackNodeSupport.durationMillis(7, SpatialDurationUnit.DAYS), TrackNodeSupport.durationMillis(1, SpatialDurationUnit.WEEKS));
        assertEquals(302_400_000d, TrackNodeSupport.durationMillis(0.5, SpatialDurationUnit.WEEKS));
        var calendar = window("2024-03-04T00:00-05:00", "America/New_York", 1, WEEKS, 1, WEEKS);
        var row = at(calendar, "2024-03-10T12:00:00Z").getFirst();
        assertEquals(601_200_000_000L, row.getLong(1) - row.getLong(0));
        assertBounds(calendar, "2024-03-10T12:00:00Z", "2024-03-04T05:00:00Z", "2024-03-11T04:00:00Z");
        var overflow = new Issues();
        assertNull(SpatialTemporalSupport.validateAndResolve(new SpatialTemporalSlicing("time", Long.MAX_VALUE, SpatialDurationUnit.WEEKS,
                null, null, null, "UTC", "start", "end"), "time", overflow));
        assertTrue(overflow.codes.contains("INVALID_SPATIAL_TEMPORAL_INTERVAL"));
    }

    @Test void monthEndsAndLeapDaysAlwaysUseOriginalAnchor() {
        var monthly = window("2024-01-31T00:00Z", "UTC", 1, MONTHS, 1, MONTHS);
        assertBounds(monthly, "2024-03-15T00:00:00Z", "2024-02-29T00:00:00Z", "2024-03-31T00:00:00Z");
        assertBounds(monthly, "2024-03-31T00:00:00Z", "2024-03-31T00:00:00Z", "2024-04-30T00:00:00Z");
        var yearly = window("2020-02-29T00:00Z", "UTC", 1, YEARS, 1, YEARS);
        assertBounds(yearly, "2024-02-28T00:00:00Z", "2023-02-28T00:00:00Z", "2024-02-29T00:00:00Z");
    }

    @Test void localDaysRespectDstButHoursAreElapsedDuration() {
        var day = window("2024-01-01T00:00-05:00", "America/New_York", 1, DAYS, 1, DAYS);
        assertBounds(day, "2024-03-10T12:00:00Z", "2024-03-10T05:00:00Z", "2024-03-11T04:00:00Z");
        assertBounds(day, "2024-11-03T12:00:00Z", "2024-11-03T04:00:00Z", "2024-11-04T05:00:00Z");
        var hours = window("2024-01-01T00:00-05:00", "America/New_York", 24, HOURS, 24, HOURS);
        var row = at(hours, "2024-03-10T12:00:00Z").getFirst();
        assertEquals(86_400_000_000L, row.getLong(1) - row.getLong(0));
    }

    @Test void overlappingGappedAndPreEpochWindowsAreHalfOpen() {
        var overlap = window("1970-01-31T00:00Z", "UTC", 2, MONTHS, 1, MONTHS);
        assertEquals(2, at(overlap, "1969-12-31T00:00:00Z").size());
        var gap = window("1970-01-31T00:00Z", "UTC", 1, MONTHS, 3, MONTHS);
        assertTrue(at(gap, "1970-03-01T00:00:00Z").isEmpty());
        assertBounds(gap, "1969-11-01T00:00:00Z", "1969-10-31T00:00:00Z", "1969-11-30T00:00:00Z");
        assertTrue(at(gap, "1969-11-30T00:00:00Z").isEmpty());
        assertTrue(gap.call(null).isEmpty());
    }

    @Test void mixedHourlyStartsAndCalendarWidthMatchBruteForceAcrossDstOverlap() {
        var anchor = ZonedDateTime.parse("2024-11-02T00:00-04:00[America/New_York]");
        var windows = new CalendarTimeWindows(anchor, 1, DAYS, 1, HOURS);
        for (int h = 0; h < 60; h++) {
            var time = anchor.plusHours(h).toInstant();
            var expected = new ArrayList<List<Long>>();
            for (int k = -60; k <= 60; k++) {
                var start = anchor.plusHours(k); var end = start.plusDays(1);
                if (!time.isBefore(start.toInstant()) && time.isBefore(end.toInstant()))
                    expected.add(List.of(CalendarTimeWindows.micros(start.toInstant()), CalendarTimeWindows.micros(end.toInstant())));
            }
            var actual = windows.call(CalendarTimeWindows.micros(time)).stream().map(r -> List.of(r.getLong(0), r.getLong(1))).toList();
            assertEquals(expected, actual, "hour " + h);
        }
    }

    @Test void skippedLocalDateDoesNotDuplicateOrCreateZeroLengthWindows() {
        var windows = window("2011-12-29T00:00-10:00", "Pacific/Apia", 1, DAYS, 1, DAYS);
        assertBounds(windows, "2011-12-30T12:00:00Z", "2011-12-30T10:00:00Z", "2011-12-31T10:00:00Z");
    }

    @Test void invalidAmbiguousOrOverPreciseReferencesHaveSafeErrors() {
        for (String reference : List.of("2024-03-10T02:30:00", "2024-11-03T01:30:00", "2024-01-01T00:00:00.0000001Z", "private-invalid")) {
            var issues = new Issues();
            assertNull(SpatialTemporalSupport.validateAndResolve(config(reference, "America/New_York", 1, null, DAYS, null), "configuration.temporalSlicing", issues));
            assertTrue(issues.codes.contains("INVALID_SPATIAL_TEMPORAL_REFERENCE"));
            assertTrue(issues.messages.stream().noneMatch(m -> m.contains(reference)));
        }
        var issues = new Issues();
        assertNotNull(SpatialTemporalSupport.validateAndResolve(config("2024-11-03T01:30:00-04:00", "America/New_York", 1, null, DAYS, null), "time", issues));
        assertFalse(issues.hasErrors());
    }

    @Test void defaultReferenceIsEpochNotLocalMidnightAndInactiveRootUnitsAreIgnored() {
        var issues = new Issues();
        var parameters = SpatialTemporalSupport.validateAndResolve(config(null, "Asia/Shanghai", 1, 1L, MONTHS, MONTHS), "time", issues);
        assertFalse(issues.hasErrors());
        assertBounds(parameters.calendar(), "1970-01-10T00:00:00Z", "1970-01-01T00:00:00Z", "1970-02-01T00:00:00Z");
    }

    @Test void invalidNumericModeUnitsAndZoneAreCompilerErrors() {
        for (var value : List.of(config(null, "UTC", 0, null, DAYS, null), config(null, "UTC", 1, 0L, DAYS, HOURS),
                config(null, "UTC", 1, 1L, DAYS, null), config(null, "invalid-zone", 1, null, DAYS, null),
                config(null, "UTC", Long.MAX_VALUE, null, YEARS, null))) {
            var issues = new Issues(); SpatialTemporalSupport.validateAndResolve(value, "time", issues);
            assertTrue(issues.hasErrors());
        }
        var value = config(null, "UTC", 1, null, DAYS, null);
        var issues = new Issues();
        SpatialTemporalSupport.validateAndResolve(new SpatialTemporalSlicing("t", 1, SpatialDurationUnit.DAYS, null, null, null, "UTC", "s", "e",
                new SpatialCalendarWindowOptions(null, DAYS, null)), "time", issues);
        assertTrue(issues.codes.contains("INVALID_SPATIAL_TEMPORAL_MODE"));
        assertTrue(value.usesCalendar());
    }

    @Test void runtimeLimitsAndArithmeticFailuresDoNotExposeValuesOrCauses() {
        var dense = window("1970-01-01T00:00Z", "UTC", 1, MONTHS, 1, SECONDS);
        var limit = assertThrows(IllegalArgumentException.class, () -> at(dense, "2024-01-01T00:00:00Z"));
        assertEquals("SPATIAL_CALENDAR_WINDOW_LIMIT_EXCEEDED", limit.getMessage()); assertNull(limit.getCause());
        var day = window("1970-01-01T00:00Z", "UTC", 1, DAYS, 1, DAYS);
        var range = assertThrows(IllegalArgumentException.class, () -> day.call(Long.MIN_VALUE));
        assertEquals("SPATIAL_CALENDAR_WINDOW_RANGE_INVALID", range.getMessage()); assertNull(range.getCause());
    }

    static SpatialTemporalSlicing config(String reference, String zone, long width, Long repeat,
            SpatialCalendarWindowOptions.Unit widthUnit, SpatialCalendarWindowOptions.Unit stepUnit) {
        return new SpatialTemporalSlicing("event_time", width, SpatialDurationUnit.HOURS, repeat, null, reference, zone, "start", "end",
                new SpatialCalendarWindowOptions(SpatialCalendarWindowOptions.Mode.CALENDAR, widthUnit, stepUnit));
    }
    private CalendarTimeWindows window(String reference, String zone, long width, SpatialCalendarWindowOptions.Unit wu, long step, SpatialCalendarWindowOptions.Unit su) {
        return new CalendarTimeWindows(OffsetDateTime.parse(reference).atZoneSameInstant(ZoneId.of(zone)), width, wu, step, su);
    }
    private List<Row> at(CalendarTimeWindows windows, String time) { return windows.call(CalendarTimeWindows.micros(Instant.parse(time))); }
    private void assertBounds(CalendarTimeWindows windows, String time, String start, String end) {
        var rows = at(windows, time); assertEquals(1, rows.size());
        assertEquals(Instant.parse(start), CalendarTimeWindows.fromMicros(rows.getFirst().getLong(0)));
        assertEquals(Instant.parse(end), CalendarTimeWindows.fromMicros(rows.getFirst().getLong(1)));
    }
    private static class Issues implements CanvasNodeIssueSink {
        final List<String> codes = new ArrayList<>(), messages = new ArrayList<>();
        public void error(String code, String message, String path) { codes.add(code); messages.add(message); }
        public void warning(String code, String message, String path) { }
        public boolean hasErrors() { return !codes.isEmpty(); }
    }
}

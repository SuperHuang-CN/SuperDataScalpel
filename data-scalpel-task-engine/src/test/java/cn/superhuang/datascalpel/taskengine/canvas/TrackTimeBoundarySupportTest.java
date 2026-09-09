package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.TrackTimeBoundaryUnit;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrackTimeBoundarySupportTest {
    @Test
    void fixedDurationsUseHalfOpenBucketsIncludingNegativeFractionalInstants() {
        var bucket = new TrackTimeBoundarySupport.Bucket(1, TrackTimeBoundaryUnit.MILLISECONDS,
                ZonedDateTime.parse("1970-01-01T00:00:00Z"));
        assertEquals(-1L, bucket.call(at("1969-12-31T23:59:59.999999Z")));
        assertEquals(0L, bucket.call(at("1970-01-01T00:00:00Z")));
        assertEquals(1L, bucket.call(at("1970-01-01T00:00:00.001Z")));
        assertNull(bucket.call(null));
    }

    @Test
    void monthEndClampingAlwaysUsesOriginalReferenceAndSupportsPreReferenceEvents() {
        var bucket = new TrackTimeBoundarySupport.Bucket(1, TrackTimeBoundaryUnit.MONTHS,
                ZonedDateTime.parse("2024-01-31T12:00:00Z"));
        assertEquals(-1L, bucket.call(at("2024-01-01T00:00:00Z")));
        assertEquals(0L, bucket.call(at("2024-02-29T11:59:59Z")));
        assertEquals(1L, bucket.call(at("2024-02-29T12:00:00Z")));
        assertEquals(1L, bucket.call(at("2024-03-30T12:00:00Z")));
        assertEquals(2L, bucket.call(at("2024-03-31T12:00:00Z")));
        var leapYear = new TrackTimeBoundarySupport.Bucket(1, TrackTimeBoundaryUnit.YEARS,
                ZonedDateTime.parse("2024-02-29T12:00:00Z"));
        assertEquals(1L, leapYear.call(at("2025-02-28T12:00:00Z")));
        assertEquals(3L, leapYear.call(at("2028-02-28T12:00:00Z")));
        assertEquals(4L, leapYear.call(at("2028-02-29T12:00:00Z")));
    }

    @Test
    void calendarDayTracksLocalMidnightAcrossBothDaylightSavingTransitions() {
        var spring = new TrackTimeBoundarySupport.Bucket(1, TrackTimeBoundaryUnit.DAYS,
                ZonedDateTime.parse("2026-03-08T00:00:00-05:00[America/New_York]"));
        assertEquals(0L, spring.call(at("2026-03-09T03:59:59Z")));
        assertEquals(1L, spring.call(at("2026-03-09T04:00:00Z")));
        var autumn = new TrackTimeBoundarySupport.Bucket(1, TrackTimeBoundaryUnit.DAYS,
                ZonedDateTime.parse("2026-11-01T00:00:00-04:00[America/New_York]"));
        assertEquals(0L, autumn.call(at("2026-11-02T04:59:59Z")));
        assertEquals(1L, autumn.call(at("2026-11-02T05:00:00Z")));
    }

    private static Timestamp at(String instant) { return Timestamp.from(Instant.parse(instant)); }
}

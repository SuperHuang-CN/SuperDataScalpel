package cn.superhuang.datascalpel.taskengine.jdbc.incremental;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcIncrementalOffsetTest {

    @Test
    void roundTripsBoundedOffsetWithoutLosingSourceIdentity() {
        JdbcIncrementalOffset offset = JdbcIncrementalOffset.at(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                "TIMESTAMP",
                Instant.parse("2026-08-12T03:30:45.123456Z")
        );

        JdbcIncrementalOffset restored = JdbcIncrementalOffset.parse(offset.json());

        assertEquals(offset, restored);
        assertFalse(restored.lowerUnbounded());
        assertEquals(Instant.parse("2026-08-12T03:30:45.123456Z"), restored.endTime());
    }

    @Test
    void roundTripsEarliestSentinel() {
        JdbcIncrementalOffset offset = JdbcIncrementalOffset.earliest(
                "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                "TIMESTAMP_NTZ"
        );

        JdbcIncrementalOffset restored = JdbcIncrementalOffset.parse(offset.json());

        assertTrue(restored.lowerUnbounded());
        assertNull(restored.endTime());
        assertEquals("TIMESTAMP_NTZ", restored.temporalType());
    }
}

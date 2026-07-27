package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileDatasetParseRetryPolicyTest {

    @Test
    void appliesExponentialBackoff() {
        assertEquals(Duration.ofSeconds(30), FileDatasetParseRetryPolicy.delay(30, 1));
        assertEquals(Duration.ofSeconds(60), FileDatasetParseRetryPolicy.delay(30, 2));
        assertEquals(Duration.ofSeconds(120), FileDatasetParseRetryPolicy.delay(30, 3));
    }

    @Test
    void capsLongSchedulesAtTwentyFourHours() {
        assertEquals(
                FileDatasetParseRetryPolicy.MAX_DELAY,
                FileDatasetParseRetryPolicy.delay(3_600, 10)
        );
    }

    @Test
    void rejectsInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> FileDatasetParseRetryPolicy.delay(0, 1));
        assertThrows(IllegalArgumentException.class, () -> FileDatasetParseRetryPolicy.delay(30, 0));
    }
}

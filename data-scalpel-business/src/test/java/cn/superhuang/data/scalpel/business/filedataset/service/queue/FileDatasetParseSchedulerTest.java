package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileDatasetParseSchedulerTest {

    @Test
    void dynamicallyFillsOnlyConfiguredFreeSlots() {
        assertEquals(2, FileDatasetParseScheduler.availableSlots(true, 2, 0));
        assertEquals(4, FileDatasetParseScheduler.availableSlots(true, 6, 2));
        assertEquals(0, FileDatasetParseScheduler.availableSlots(true, 2, 6));
    }

    @Test
    void closingQueueStopsNewClaimsWithoutChangingRunningCount() {
        assertEquals(0, FileDatasetParseScheduler.availableSlots(false, 16, 3));
    }

    @Test
    void rejectsImpossibleRuntimeValues() {
        assertThrows(IllegalArgumentException.class, () -> FileDatasetParseScheduler.availableSlots(true, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> FileDatasetParseScheduler.availableSlots(true, 17, 0));
        assertThrows(IllegalArgumentException.class, () -> FileDatasetParseScheduler.availableSlots(true, 2, -1));
    }
}

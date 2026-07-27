package cn.superhuang.data.scalpel.business.filedataset.domain;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileDatasetQueueStateTest {

    @Test
    void datasetUpdateHasNoRevisionState() {
        FileDataset dataset = FileDataset.create(
                null, "道路", FileDatasetType.CSV, "{\"charset\":\"UTF-8\"}", null
        );

        dataset.update(null, "道路新名称", "{\"charset\":\"GBK\"}", "说明");

        assertEquals("道路新名称", dataset.getName());
        assertEquals("{\"charset\":\"GBK\"}", dataset.getParsingOptions());
        assertEquals("说明", dataset.getDescription());
    }

    @Test
    void protectsInitialLoadTransitionsWithCurrentJobId() {
        FileDatasetTable table = newTable();
        UUID jobId = UUID.randomUUID();

        table.queueInitialLoad(jobId);
        assertEquals(FileDatasetParseStatus.QUEUED, table.getParseStatus());
        assertEquals(jobId, table.getCurrentLoadJobId());

        assertThrows(IllegalStateException.class, () -> table.startLoad(UUID.randomUUID()));

        table.startLoad(jobId);
        assertEquals(FileDatasetParseStatus.PARSING, table.getParseStatus());
        table.completeInitialLoad(jobId, "{\"sampledRecordCount\":1}", true);

        assertEquals(FileDatasetParseStatus.READY, table.getParseStatus());
        assertNull(table.getCurrentLoadJobId());
    }

    @Test
    void supportsRetryAndRejectsConcurrentDataChanges() {
        FileDatasetTable table = readyTable();
        UUID firstJobId = UUID.randomUUID();

        table.beginDataChange(firstJobId);
        assertThrows(
                IllegalStateException.class,
                () -> table.beginDataChange(UUID.randomUUID()),
                "同一张逻辑表只能有一个装载任务"
        );

        table.startLoad(firstJobId);
        table.requeueLoad(firstJobId);
        assertEquals(FileDatasetParseStatus.READY, table.getParseStatus());
        assertEquals(firstJobId, table.getCurrentLoadJobId());

        table.clearCurrentLoad(firstJobId);
        assertNull(table.getCurrentLoadJobId());
        assertEquals(FileDatasetParseStatus.READY, table.getParseStatus());
    }

    @Test
    void handsPreparationJobOffToValidationJob() {
        FileDatasetTable table = newTable();
        UUID preparationJobId = UUID.randomUUID();
        UUID validationJobId = UUID.randomUUID();

        table.queueInitialLoad(preparationJobId);
        table.startLoad(preparationJobId);
        table.handoffCurrentLoad(preparationJobId, validationJobId);

        assertEquals(validationJobId, table.getCurrentLoadJobId());
        assertEquals(FileDatasetParseStatus.QUEUED, table.getParseStatus());
        assertThrows(
                IllegalStateException.class,
                () -> table.completeInitialLoad(preparationJobId, "{}", true)
        );
    }

    private static FileDatasetTable readyTable() {
        FileDatasetTable table = newTable();
        UUID initialJobId = UUID.randomUUID();
        table.queueInitialLoad(initialJobId);
        table.startLoad(initialJobId);
        table.completeInitialLoad(initialJobId, "{}", true);
        return table;
    }

    private static FileDatasetTable newTable() {
        return FileDatasetTable.create(UUID.randomUUID(), "roads", "道路");
    }
}

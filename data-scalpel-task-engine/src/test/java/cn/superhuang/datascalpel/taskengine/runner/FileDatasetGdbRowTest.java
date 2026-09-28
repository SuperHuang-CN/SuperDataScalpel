package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFeature;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbField;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFieldType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbSchema;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FileDatasetGdbRowTest {
    private static final FileGdbSchema SCHEMA = new FileGdbSchema(
            "a00000009", "events", FileGdbLayerType.TABLE,
            List.of(new FileGdbField("occurred_at", "occurred_at", FileGdbFieldType.TIMESTAMP, true, 8)), null);

    @Test
    void readsGdbDateAsWallClockWithoutApplyingTheJvmTimezone() {
        var instant = Instant.parse("2026-09-23T12:34:56.123Z");
        var feature = new FileGdbFeature(1, Map.of("occurred_at", instant), null);
        var row = FileDatasetBatchReaderRegistry.gdbRow(feature, SCHEMA,
                List.of(column(PlatformDataType.TIMESTAMP_NTZ)));
        assertEquals(LocalDateTime.of(2026, 9, 23, 12, 34, 56, 123_000_000), row.get(0));
        var timestampRow = FileDatasetBatchReaderRegistry.gdbRow(feature, SCHEMA,
                List.of(column(PlatformDataType.TIMESTAMP)));
        assertEquals(Timestamp.from(instant), timestampRow.get(0));
    }

    @Test
    void preservesNullDate() {
        var row = FileDatasetBatchReaderRegistry.gdbRow(new FileGdbFeature(1, Map.of(), null), SCHEMA,
                List.of(column(PlatformDataType.TIMESTAMP_NTZ)));
        assertNull(row.get(0));
    }

    private static CanvasColumnSchema column(PlatformDataType type) {
        return new CanvasColumnSchema("occurred_at", type, null, null, null, true, null, false, false, null);
    }
}

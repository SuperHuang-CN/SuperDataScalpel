package cn.superhuang.data.scalpel.business.filedataset.domain;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileDatasetTableSourceStateTest {

    private static final Instant ACTIVATED_AT = Instant.parse("2026-07-24T00:00:00Z");

    @Test
    void createsOnlyAnAlreadyEffectiveSource() {
        UUID tableId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        FileDatasetTableSource source = FileDatasetTableSource.create(
                tableId, fileId, "roads.csv", "__file__", 0, 10,
                "schema-1", "{\"recordCount\":10}", ACTIVATED_AT
        );

        assertEquals(tableId, source.getFileDatasetTableId());
        assertEquals(fileId, source.getSourceFileId());
        assertEquals(0, source.getSourceOrder());
        assertEquals(10, source.getRowCount());
        assertEquals("schema-1", source.getSchemaFingerprint());
        assertEquals(ACTIVATED_AT, source.getActivatedAt());
    }

    @Test
    void replacesSourceInPlaceAndKeepsIdentityAndOrder() {
        FileDatasetTableSource source = FileDatasetTableSource.create(
                UUID.randomUUID(), UUID.randomUUID(), "roads-2025.csv", "__file__",
                2, 10, "schema-1", "{\"year\":2025}", ACTIVATED_AT
        );
        UUID sourceId = UUID.randomUUID();
        ReflectionTestUtils.setField(source, "id", sourceId);
        UUID replacementFileId = UUID.randomUUID();
        Instant replacementTime = ACTIVATED_AT.plusSeconds(3600);

        source.replace(
                replacementFileId, "roads-2026.csv", "__file__", 12,
                "schema-1", "{\"year\":2026}", replacementTime
        );

        assertEquals(sourceId, source.getId());
        assertEquals(2, source.getSourceOrder());
        assertEquals(replacementFileId, source.getSourceFileId());
        assertEquals("roads-2026.csv", source.getSourceName());
        assertEquals(12, source.getRowCount());
        assertEquals(replacementTime, source.getActivatedAt());
    }

    @Test
    void validatesRequiredCurrentSourceFieldsAndSupportsOrderCompression() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FileDatasetTableSource.create(
                        UUID.randomUUID(), UUID.randomUUID(), "roads.csv", "__file__",
                        -1, 1, "schema", "{}", ACTIVATED_AT
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> FileDatasetTableSource.create(
                        UUID.randomUUID(), UUID.randomUUID(), "roads.csv", "__file__",
                        0, -1, "schema", "{}", ACTIVATED_AT
                )
        );

        FileDatasetTableSource source = FileDatasetTableSource.create(
                UUID.randomUUID(), UUID.randomUUID(), "roads.csv", "__file__",
                3, 1, "schema", "{}", ACTIVATED_AT
        );
        source.setSourceOrder(1);
        assertEquals(1, source.getSourceOrder());
    }
}

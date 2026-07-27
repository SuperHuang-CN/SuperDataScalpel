package cn.superhuang.data.scalpel.filegdb.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.filegdb.FileGdbOpenOptions;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.testutil.TestFileGdbBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GdbTableIndexReaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void readsFourFiveAndSixByteOffsetsAndDeletedSlots() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        assertIndex(fixture, "ScalarPointXY", 4, true);
        assertIndex(fixture, "PointXYZ", 5, false);
        assertIndex(fixture, "PointXYM", 6, false);
    }

    private static void assertIndex(
            TestFileGdbBuilder.Fixture fixture,
            String layer,
            int expectedWidth,
            boolean deletedSecondSlot) {
        String physicalName = fixture.layerId(layer);
        try (LocalFileGdbSource source = LocalFileGdbSource.open(
                        fixture.directory(),
                        FileGdbOpenOptions.defaults());
                GdbTableIndexReader reader = GdbTableIndexReader.open(
                        source,
                        physicalName + ".gdbtablx",
                        physicalName,
                        FileGdbReadLimits.defaults())) {
            assertEquals(expectedWidth, reader.offsetWidth());
            assertEquals(1_024, reader.slotCount());
            assertTrue(reader.recordOffset(0) > 0);
            assertEquals(0, reader.recordOffset(1));
            if (deletedSecondSlot) {
                assertTrue(reader.recordOffset(2) > 0);
                assertEquals(3, reader.declaredRowCount());
            }
        }
    }
}

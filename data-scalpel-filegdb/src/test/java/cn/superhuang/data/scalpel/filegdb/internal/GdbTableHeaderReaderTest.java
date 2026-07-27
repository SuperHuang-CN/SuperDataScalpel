package cn.superhuang.data.scalpel.filegdb.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.filegdb.FileGdbOpenOptions;
import cn.superhuang.data.scalpel.filegdb.FileGdbReadLimits;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbFieldType;
import cn.superhuang.data.scalpel.filegdb.model.FileGdbLayerType;
import cn.superhuang.data.scalpel.filegdb.testutil.TestFileGdbBuilder;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GdbTableHeaderReaderTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesFieldOrderAliasesAndSpatialQuantizationMetadata() throws Exception {
        var fixture = TestFileGdbBuilder.scalarAndPoints(temporaryDirectory);
        String physicalName = fixture.layerId("PointXYZM");
        try (LocalFileGdbSource source = LocalFileGdbSource.open(
                fixture.directory(),
                FileGdbOpenOptions.defaults())) {
            GdbTableDefinition definition = GdbTableHeaderReader.read(
                    source,
                    physicalName + ".gdbtable",
                    physicalName,
                    FileGdbReadLimits.defaults());

            assertEquals(FileGdbLayerType.POINT, definition.layerType());
            assertEquals(2, definition.fields().size());
            assertEquals(FileGdbFieldType.OID, definition.fields().get(0).publicField().type());
            assertEquals(FileGdbFieldType.SHAPE, definition.fields().get(1).publicField().type());
            assertFalse(definition.fields().get(0).publicField().nullable());
            assertTrue(definition.fields().get(1).publicField().nullable());
            assertTrue(definition.spatialReference().hasZ());
            assertTrue(definition.spatialReference().hasM());
            assertEquals(1_000, definition.spatialReference().xyScale());
            assertEquals(10, definition.spatialReference().zScale());
            assertEquals(10, definition.spatialReference().mScale());
        }
    }
}

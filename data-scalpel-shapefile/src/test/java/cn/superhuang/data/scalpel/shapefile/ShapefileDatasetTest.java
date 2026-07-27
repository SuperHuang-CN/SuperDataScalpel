package cn.superhuang.data.scalpel.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileFeature;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileFieldType;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePoint;
import cn.superhuang.data.scalpel.shapefile.testutil.TestShapefileBuilder;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ShapefileDatasetTest {
    private static final ShapefileEnvelope ENVELOPE =
            new ShapefileEnvelope(1, 2, 5, 6, 0, 0, 0, 0);

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsSchemaAttributesGeometryDeletedRowsAndNullShape() throws Exception {
        Path shp = new TestShapefileBuilder(
                temporaryDirectory, "roads", ShapefileShapeType.POINT, ENVELOPE)
                .field("Name", 'C', 20, 0)
                .field("Count", 'N', 12, 0)
                .field("Rate", 'N', 12, 3)
                .field("Active", 'L', 1, 0)
                .field("Opened", 'D', 8, 0)
                .record(
                        TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null),
                        false, " 道路", "123456789012", "12.340", "Y", "20240229")
                .record(
                        TestShapefileBuilder.point(ShapefileShapeType.POINT, 3, 4, null, null),
                        true, "deleted", "2", "2.0", "N", "20240101")
                .record(TestShapefileBuilder.nullShape(), false, null, null, null, "?", null)
                .dbfCharset(StandardCharsets.UTF_8)
                .cpg("\uFEFF65001\n")
                .prj("LOCAL_CS[\"test\"]")
                .write();

        try (ShapefileDataset dataset = ShapefileDataset.open(shp)) {
            assertEquals(ShapefileShapeType.POINT, dataset.schema().shapeType());
            assertEquals(3, dataset.schema().recordCount());
            assertEquals(StandardCharsets.UTF_8, dataset.schema().dbfCharset());
            assertEquals("LOCAL_CS[\"test\"]", dataset.schema().spatialReference().wkt());
            assertEquals(
                    List.of(
                            ShapefileFieldType.STRING,
                            ShapefileFieldType.INTEGER,
                            ShapefileFieldType.DECIMAL,
                            ShapefileFieldType.BOOLEAN,
                            ShapefileFieldType.DATE),
                    dataset.schema().fields().stream().map(field -> field.type()).toList());

            List<ShapefileFeature> features = new ArrayList<>();
            try (ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(10))) {
                cursor.forEachRemaining(features::add);
            }
            assertEquals(2, features.size());
            ShapefileFeature first = features.getFirst();
            assertEquals(1, first.recordNumber());
            assertEquals(" 道路", first.attribute("Name"));
            assertEquals(new BigInteger("123456789012"), first.attribute("Count"));
            assertEquals(new BigDecimal("12.340"), first.attribute("Rate"));
            assertEquals(true, first.attribute("Active"));
            assertEquals(LocalDate.of(2024, 2, 29), first.attribute("Opened"));
            ShapefilePoint point = assertInstanceOf(ShapefilePoint.class, first.geometry());
            assertEquals(1, point.x());
            assertEquals(2, point.y());
            assertThrows(UnsupportedOperationException.class, () -> first.attributes().put("x", 1));

            assertEquals(3, features.get(1).recordNumber());
            assertNull(features.get(1).geometry());
            assertNull(features.get(1).attribute("Name"));
            assertNull(features.get(1).attribute("Active"));
        }
    }

    @Test
    void appliesFeatureLimitToActiveRows() throws Exception {
        Path shp = new TestShapefileBuilder(
                temporaryDirectory, "limited", ShapefileShapeType.POINT, ENVELOPE)
                .field("Name", 'C', 8, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null), true, "skip")
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 2, 3, null, null), false, "first")
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 3, 4, null, null), false, "second")
                .write();

        try (ShapefileDataset dataset = ShapefileDataset.open(shp);
                ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(1))) {
            assertTrue(cursor.hasNext());
            assertEquals(2, cursor.next().recordNumber());
            assertFalse(cursor.hasNext());
        }
    }

    @Test
    void defaultsToGb18030WithoutEncodingMetadata() throws Exception {
        Path shp = new TestShapefileBuilder(
                temporaryDirectory, "gb", ShapefileShapeType.POINT, ENVELOPE)
                .field("名称", 'C', 20, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null), false, "道路")
                .dbfCharset(java.nio.charset.Charset.forName("GB18030"))
                .write();

        try (ShapefileDataset dataset = ShapefileDataset.open(shp);
                ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(1))) {
            assertEquals("GB18030", dataset.schema().dbfCharset().name());
            assertEquals("道路", cursor.next().attribute("名称"));
        }
    }

    @Test
    void closesOwnedCursorsAndRejectsExcessiveLimits() throws Exception {
        Path shp = new TestShapefileBuilder(
                temporaryDirectory, "close", ShapefileShapeType.POINT, ENVELOPE)
                .field("Name", 'C', 8, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null), false, "one")
                .write();

        ShapefileDataset dataset = ShapefileDataset.open(shp);
        ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(1));
        assertThrows(
                ShapefileException.class,
                () -> dataset.openCursor(ShapefileReadOptions.limit(ShapefileReadLimits.DEFAULT_MAX_FEATURES_PER_CURSOR + 1)));
        dataset.close();
        dataset.close();
        assertEquals(ShapefileErrorCode.CLOSED, assertThrows(ShapefileException.class, cursor::hasNext).errorCode());
        assertEquals(
                ShapefileErrorCode.CLOSED,
                assertThrows(ShapefileException.class, () -> dataset.openCursor(ShapefileReadOptions.limit(1))).errorCode());
    }
}

package cn.superhuang.data.scalpel.shapefile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import cn.superhuang.data.scalpel.shapefile.model.ShapefileFeature;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileFieldType;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefilePoint;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReferenceShapefileCompatibilityTest {
    @Test
    void readsApacheLicensedSparkShpPointFixture() {
        String fixture = System.getProperty("shapefile.referenceFixture");
        assumeTrue(fixture != null && !fixture.isBlank(), "set shapefile.referenceFixture to enable this test");

        try (ShapefileDataset dataset = ShapefileDataset.open(Path.of(fixture))) {
            assertEquals(ShapefileShapeType.POINT, dataset.schema().shapeType());
            assertEquals(3, dataset.schema().recordCount());
            assertEquals("UTF-8", dataset.schema().dbfCharset().name());
            assertEquals(
                    "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\",0.0174532925199433]]",
                    dataset.schema().spatialReference().wkt());
            assertEquals(
                    List.of("Id", "aText", "aFloat", "aDouble", "aShort", "aLong", "aDate"),
                    dataset.schema().fields().stream().map(field -> field.name()).toList());
            assertEquals(
                    List.of(
                            ShapefileFieldType.INTEGER,
                            ShapefileFieldType.STRING,
                            ShapefileFieldType.DECIMAL,
                            ShapefileFieldType.DECIMAL,
                            ShapefileFieldType.INTEGER,
                            ShapefileFieldType.INTEGER,
                            ShapefileFieldType.DATE),
                    dataset.schema().fields().stream().map(field -> field.type()).toList());
            List<ShapefileFeature> features = new ArrayList<>();
            try (ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(10))) {
                cursor.forEachRemaining(features::add);
            }
            assertEquals(3, features.size());
            double[][] coordinates = {
                    {Double.longBitsToDouble(0xC0533F6759287171L), Double.longBitsToDouble(0x404375E45D91B9AFL)},
                    {Double.longBitsToDouble(0xC053454AC493F15FL), Double.longBitsToDouble(0x4043778053DCC115L)},
                    {Double.longBitsToDouble(0xC0533FF4C34C967CL), Double.longBitsToDouble(0x404371100A8EF96CL)}
            };
            for (int index = 0; index < features.size(); index++) {
                ShapefileFeature feature = features.get(index);
                assertEquals(index + 1, feature.recordNumber());
                assertEquals(BigInteger.valueOf(index), feature.attribute("Id"));
                assertEquals("aText" + (index + 1), feature.attribute("aText"));
                int value = (index + 1) * 10;
                assertEquals(new BigDecimal(value + ".0000"), feature.attribute("aFloat"));
                assertEquals(new BigDecimal(value + ".0000000000"), feature.attribute("aDouble"));
                assertEquals(BigInteger.valueOf(value), feature.attribute("aShort"));
                assertEquals(BigInteger.valueOf(value), feature.attribute("aLong"));
                assertEquals(LocalDate.of(2019, 11, 20 + index), feature.attribute("aDate"));
                ShapefilePoint point = assertInstanceOf(ShapefilePoint.class, feature.geometry());
                assertEquals(coordinates[index][0], point.x());
                assertEquals(coordinates[index][1], point.y());
            }
        }
    }
}

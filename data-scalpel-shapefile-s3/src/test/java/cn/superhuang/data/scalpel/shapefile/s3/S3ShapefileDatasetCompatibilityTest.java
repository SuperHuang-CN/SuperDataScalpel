package cn.superhuang.data.scalpel.shapefile.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import cn.superhuang.data.scalpel.shapefile.ShapefileDataset;
import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileFeatureCursor;
import cn.superhuang.data.scalpel.shapefile.ShapefileReadOptions;
import cn.superhuang.data.scalpel.shapefile.ShapefileReadLimits;
import cn.superhuang.data.scalpel.shapefile.ShapefileSource;
import cn.superhuang.data.scalpel.shapefile.ShapefileSourceType;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileEnvelope;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileFeature;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileSchema;
import cn.superhuang.data.scalpel.shapefile.model.ShapefileShapeType;
import cn.superhuang.data.scalpel.shapefile.model.geometry.ShapefileGeometry;
import cn.superhuang.data.scalpel.shapefile.testutil.TestShapefileBuilder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class S3ShapefileDatasetCompatibilityTest {
    private static final ShapefileEnvelope ENVELOPE =
            new ShapefileEnvelope(0, 0, 10, 10, -10, 10, -1.0E39, 100);

    @TempDir
    Path temporaryDirectory;

    @Test
    void localAndS3SourcesMatchForAllGeometryFamiliesAndDimensions() throws Exception {
        List<Path> fixtures = List.of(
                fixture(
                        "point-zm",
                        ShapefileShapeType.POINT_Z,
                        TestShapefileBuilder.point(ShapefileShapeType.POINT_Z, 1, 2, 3.5, 4.5)),
                fixture(
                        "multipoint-m",
                        ShapefileShapeType.MULTIPOINT_M,
                        TestShapefileBuilder.multiPoint(
                                ShapefileShapeType.MULTIPOINT_M,
                                new double[] {0, 2},
                                new double[] {1, 3},
                                null,
                                new double[] {-1.0E39, 9})),
                fixture(
                        "polyline-zm",
                        ShapefileShapeType.POLYLINE_Z,
                        TestShapefileBuilder.multipart(
                                ShapefileShapeType.POLYLINE_Z,
                                new int[] {2, 2},
                                new double[] {0, 1, 2, 3},
                                new double[] {0, 1, 1, 2},
                                new double[] {4, 5, 6, 7},
                                new double[] {8, 9, 10, 11})),
                fixture(
                        "polygon-m",
                        ShapefileShapeType.POLYGON_M,
                        TestShapefileBuilder.multipart(
                                ShapefileShapeType.POLYGON_M,
                                new int[] {5},
                                new double[] {0, 4, 4, 0, 0},
                                new double[] {0, 0, 4, 4, 0},
                                null,
                                new double[] {1, 2, 3, 4, 1})));

        for (Path fixture : fixtures) {
            assertEquivalent(fixture);
        }
    }

    @Test
    void preservesPhysicalRecordNumbersAcrossDeletedAndNullRecords() throws Exception {
        Path shp = new TestShapefileBuilder(
                        temporaryDirectory,
                        "alignment",
                        ShapefileShapeType.POINT,
                        ENVELOPE)
                .field("Name", 'C', 20, 0)
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 1, null, null), false, "first")
                .record(TestShapefileBuilder.point(ShapefileShapeType.POINT, 2, 2, null, null), true, "deleted")
                .record(TestShapefileBuilder.nullShape(), false, "null-shape")
                .cpg("65001")
                .prj("LOCAL_CS[\"alignment\"]")
                .write();

        TestS3ObjectAccess access = load(shp);
        try (ShapefileDataset dataset = ShapefileDataset.open(source(access, shp));
                ShapefileFeatureCursor cursor = dataset.openCursor(ShapefileReadOptions.limit(2))) {
            ShapefileFeature first = cursor.next();
            ShapefileFeature third = cursor.next();
            assertEquals(1, first.recordNumber());
            assertEquals(3, third.recordNumber());
            assertEquals("null-shape", third.attribute("Name"));
            assertEquals(null, third.geometry());
            assertFalse(cursor.hasNext());
        }
    }

    @Test
    void enforcesRemoteObjectLimitsBeforeAnyRangeRequest() throws Exception {
        Path shp = fixture(
                "oversized",
                ShapefileShapeType.POINT,
                TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null));
        TestS3ObjectAccess access = load(shp);
        access.declaredSizes.put(
                ShapefileComponent.SHP,
                ShapefileReadLimits.DEFAULT_MAX_COMPONENT_FILE_BYTES + 1);

        ShapefileException exception = assertThrows(
                ShapefileException.class,
                () -> ShapefileDataset.open(source(access, shp)));
        assertEquals(ShapefileErrorCode.LIMIT_EXCEEDED, exception.errorCode());
        assertTrue(access.rangeCalls.isEmpty());
    }

    @Test
    void denseShxValidationUsesOneRemoteRequestPerTouchedBlock() throws Exception {
        int recordCount = 9_000;
        TestShapefileBuilder builder = new TestShapefileBuilder(
                temporaryDirectory,
                "dense-index",
                ShapefileShapeType.POINT,
                ENVELOPE);
        byte[] point = TestShapefileBuilder.point(ShapefileShapeType.POINT, 1, 2, null, null);
        for (int record = 0; record < recordCount; record++) {
            builder.record(point, false);
        }
        Path shp = builder.write();
        TestS3ObjectAccess access = load(shp);
        int blockSize = 64 * 1024;
        ShapefileSource source = S3ShapefileSource.create(
                access,
                S3ShapefileLocation.fromShpKey("bucket", "fixtures/dense-index.shp"),
                new S3ShapefileOptions(blockSize, 8L * blockSize));

        try (ShapefileDataset dataset = ShapefileDataset.open(source)) {
            assertEquals(recordCount, dataset.schema().recordCount());
        }

        assertEquals(
                blocks(Files.size(shp.resolveSibling("dense-index.shx")), blockSize),
                requests(access, ShapefileComponent.SHX));
        assertEquals(
                blocks(Files.size(shp), blockSize),
                requests(access, ShapefileComponent.SHP));
        assertEquals(1, requests(access, ShapefileComponent.DBF));
    }

    private Path fixture(String name, ShapefileShapeType type, byte[] geometry) throws Exception {
        return new TestShapefileBuilder(temporaryDirectory, name, type, ENVELOPE)
                .field("Name", 'C', 20, 0)
                .record(geometry, false, "道路-" + name)
                .dbfCharset(StandardCharsets.UTF_8)
                .cpg("65001")
                .prj("LOCAL_CS[\"" + name + "\"]")
                .write();
    }

    private void assertEquivalent(Path shp) throws Exception {
        TestS3ObjectAccess access = load(shp);
        try (ShapefileDataset local = ShapefileDataset.open(shp);
                ShapefileDataset s3 = ShapefileDataset.open(source(access, shp))) {
            assertEquals(ShapefileSourceType.S3, s3.sourceInfo().type());
            assertEquals(local.schema(), s3.schema());
            assertEquals(readAll(local), readAll(s3));
        }
        assertFalse(access.rangeCalls.isEmpty());
        assertTrue(access.rangeCalls.stream().allMatch(call -> call.start() >= 0 && call.end() >= call.start()));
    }

    private static List<FeatureValue> readAll(ShapefileDataset dataset) {
        List<FeatureValue> values = new ArrayList<>();
        try (ShapefileFeatureCursor cursor = dataset.openCursor(
                ShapefileReadOptions.limit(Math.toIntExact(Math.max(1, dataset.schema().recordCount()))))) {
            while (cursor.hasNext()) {
                ShapefileFeature feature = cursor.next();
                values.add(new FeatureValue(
                        feature.recordNumber(),
                        feature.attributes(),
                        feature.geometry()));
            }
        }
        return values;
    }

    private static ShapefileSource source(TestS3ObjectAccess access, Path shp) {
        return S3ShapefileSource.create(
                access,
                S3ShapefileLocation.fromShpKey("bucket", "fixtures/" + shp.getFileName()),
                new S3ShapefileOptions(64 * 1024, 4L * 64 * 1024));
    }

    private static TestS3ObjectAccess load(Path shp) throws Exception {
        TestS3ObjectAccess access = new TestS3ObjectAccess();
        String fileName = shp.getFileName().toString();
        String stem = fileName.substring(0, fileName.length() - 4);
        for (ShapefileComponent component : ShapefileComponent.values()) {
            Path componentPath = shp.resolveSibling(stem + "." + component.extension());
            if (Files.exists(componentPath)) {
                access.objects.put(component, Files.readAllBytes(componentPath));
            }
        }
        return access;
    }

    private static long requests(TestS3ObjectAccess access, ShapefileComponent component) {
        return access.rangeCalls.stream().filter(call -> call.component() == component).count();
    }

    private static long blocks(long size, int blockSize) {
        return (size + blockSize - 1) / blockSize;
    }

    private record FeatureValue(
            long recordNumber,
            Map<String, Object> attributes,
            ShapefileGeometry geometry) {
    }
}

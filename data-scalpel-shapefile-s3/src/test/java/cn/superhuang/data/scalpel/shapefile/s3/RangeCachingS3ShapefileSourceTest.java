package cn.superhuang.data.scalpel.shapefile.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cn.superhuang.data.scalpel.shapefile.ShapefileComponent;
import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import cn.superhuang.data.scalpel.shapefile.ShapefileRandomAccessObject;
import cn.superhuang.data.scalpel.shapefile.ShapefileSource;
import cn.superhuang.data.scalpel.shapefile.ShapefileSourceType;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;

class RangeCachingS3ShapefileSourceTest {
    private static final int BLOCK_SIZE = 64 * 1024;
    private static final S3ShapefileLocation LOCATION =
            S3ShapefileLocation.fromShpKey("bucket", "layers/roads.shp");

    @Test
    void capturesAllComponentSnapshotsBeforeServingReads() {
        TestS3ObjectAccess access = accessWithRequiredObjects();

        try (ShapefileSource source = S3ShapefileSource.create(
                access, LOCATION, new S3ShapefileOptions(BLOCK_SIZE, 2L * BLOCK_SIZE))) {
            assertEquals(ShapefileSourceType.S3, source.info().type());
            assertTrue(source.exists(ShapefileComponent.SHP));
            assertTrue(!source.exists(ShapefileComponent.CPG));
        }

        assertEquals(5, access.headCalls.size());
        assertTrue(access.headCalls.stream()
                .filter(call -> call.component().required())
                .noneMatch(TestS3ObjectAccess.HeadCall::allowMissing));
        assertTrue(access.headCalls.stream()
                .filter(call -> !call.component().required())
                .allMatch(TestS3ObjectAccess.HeadCall::allowMissing));
    }

    @Test
    void cachesBlocksAcrossObjectsAndEvictsWithinOneSharedBound() {
        TestS3ObjectAccess access = accessWithRequiredObjects();
        RangeCachingS3ShapefileSource source = (RangeCachingS3ShapefileSource) S3ShapefileSource.create(
                access, LOCATION, new S3ShapefileOptions(BLOCK_SIZE, 2L * BLOCK_SIZE));

        try (source;
                ShapefileRandomAccessObject shp = source.open(ShapefileComponent.SHP);
                ShapefileRandomAccessObject dbf = source.open(ShapefileComponent.DBF)) {
            assertEquals(16, shp.read(0, ByteBuffer.allocate(16)));
            assertEquals(16, shp.read(128, ByteBuffer.allocate(16)));
            assertEquals(32, shp.read(BLOCK_SIZE - 8L, ByteBuffer.allocate(32)));
            assertEquals(8, dbf.read(0, ByteBuffer.allocate(8)));

            assertEquals(3, access.rangeCalls.size());
            assertTrue(source.cachedBytes() <= 2L * BLOCK_SIZE);
            assertTrue(source.cachedBlockCount() <= 2);

            assertEquals(4, shp.read(2L * BLOCK_SIZE + 3, ByteBuffer.allocate(8)));
            assertEquals(4, access.rangeCalls.size());
        }
    }

    @Test
    void latchesSourceChangesForTheWholeSource() {
        TestS3ObjectAccess access = accessWithRequiredObjects();
        access.responseETag = "\"changed\"";
        ShapefileSource source = S3ShapefileSource.create(
                access, LOCATION, new S3ShapefileOptions(BLOCK_SIZE, BLOCK_SIZE));

        try (source; ShapefileRandomAccessObject object = source.open(ShapefileComponent.SHP)) {
            assertCode(ShapefileErrorCode.SOURCE_CHANGED, () -> object.read(0, ByteBuffer.allocate(1)));
            assertCode(ShapefileErrorCode.SOURCE_CHANGED, source::info);
            assertEquals(0, ((RangeCachingS3ShapefileSource) source).cachedBytes());
        }
    }

    @Test
    void rejectsAChangedVersionIdEvenWhenTheEtagMatches() {
        TestS3ObjectAccess access = accessWithRequiredObjects();
        access.snapshotVersionId = "version-1";
        access.responseVersionId = "version-2";
        ShapefileSource source = S3ShapefileSource.create(
                access, LOCATION, new S3ShapefileOptions(BLOCK_SIZE, BLOCK_SIZE));

        try (source; ShapefileRandomAccessObject object = source.open(ShapefileComponent.SHP)) {
            assertCode(ShapefileErrorCode.SOURCE_CHANGED, () -> object.read(0, ByteBuffer.allocate(1)));
            assertCode(ShapefileErrorCode.SOURCE_CHANGED, source::info);
        }
    }

    @Test
    void classifiesShortMalformedAndChangedRangeResponses() {
        TestS3ObjectAccess shortAccess = accessWithRequiredObjects();
        shortAccess.shortResponse = true;
        assertReadCode(ShapefileErrorCode.TRUNCATED_INPUT, shortAccess);

        TestS3ObjectAccess malformedRange = accessWithRequiredObjects();
        malformedRange.contentRangeOverride = "not-a-content-range";
        assertReadCode(ShapefileErrorCode.IO_ERROR, malformedRange);

        TestS3ObjectAccess changedLength = accessWithRequiredObjects();
        changedLength.totalLengthDelta = 1;
        assertReadCode(ShapefileErrorCode.SOURCE_CHANGED, changedLength);
    }

    @Test
    void rejectsMissingRequiredComponentsAndClosedObjects() {
        TestS3ObjectAccess missing = accessWithRequiredObjects();
        missing.objects.remove(ShapefileComponent.DBF);
        assertCode(
                ShapefileErrorCode.MISSING_COMPONENT,
                () -> S3ShapefileSource.create(
                        missing, LOCATION, new S3ShapefileOptions(BLOCK_SIZE, BLOCK_SIZE)));

        TestS3ObjectAccess access = accessWithRequiredObjects();
        ShapefileSource source = S3ShapefileSource.create(
                access, LOCATION, new S3ShapefileOptions(BLOCK_SIZE, BLOCK_SIZE));
        ShapefileRandomAccessObject object = source.open(ShapefileComponent.SHP);
        object.close();
        assertCode(ShapefileErrorCode.CLOSED, () -> object.read(0, ByteBuffer.allocate(1)));
        source.close();
        source.close();
        assertCode(ShapefileErrorCode.CLOSED, source::info);
    }

    private static void assertReadCode(ShapefileErrorCode expected, TestS3ObjectAccess access) {
        try (ShapefileSource source = S3ShapefileSource.create(
                        access, LOCATION, new S3ShapefileOptions(BLOCK_SIZE, BLOCK_SIZE));
                ShapefileRandomAccessObject object = source.open(ShapefileComponent.SHP)) {
            assertCode(expected, () -> object.read(0, ByteBuffer.allocate(1)));
        }
    }

    private static TestS3ObjectAccess accessWithRequiredObjects() {
        TestS3ObjectAccess access = new TestS3ObjectAccess();
        byte[] shp = new byte[2 * BLOCK_SIZE + 7];
        for (int index = 0; index < shp.length; index++) {
            shp[index] = (byte) index;
        }
        access.objects.put(ShapefileComponent.SHP, shp);
        access.objects.put(ShapefileComponent.SHX, new byte[100]);
        access.objects.put(ShapefileComponent.DBF, new byte[32]);
        return access;
    }

    private static void assertCode(ShapefileErrorCode expected, Runnable action) {
        assertEquals(
                expected,
                assertThrows(ShapefileException.class, action::run).errorCode());
    }
}

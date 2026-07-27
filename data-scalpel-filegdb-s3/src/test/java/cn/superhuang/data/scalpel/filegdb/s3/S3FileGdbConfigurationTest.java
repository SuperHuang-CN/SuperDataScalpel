package cn.superhuang.data.scalpel.filegdb.s3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class S3FileGdbConfigurationTest {
    @Test
    void normalizesAndValidatesLocations() {
        S3FileGdbLocation location = new S3FileGdbLocation(" test-bucket ", "datasets/sample.GDB///");

        assertEquals("test-bucket", location.bucket());
        assertEquals("datasets/sample.GDB", location.prefix());
        assertEquals("datasets/sample.GDB/gdb", location.objectKey("gdb"));
        assertEquals("s3://test-bucket/datasets/sample.GDB", location.safeLocation());

        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbLocation(null, "sample.gdb"));
        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbLocation(" ", "sample.gdb"));
        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbLocation("bucket/name", "sample.gdb"));
        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbLocation("bucket", null));
        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbLocation("bucket", "datasets/sample"));
        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbLocation("bucket", "datasets/../sample.gdb"));
        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbLocation("bucket", "datasets\\sample.gdb"));
        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbLocation("bucket", "datasets/\u0000/sample.gdb"));
    }

    @Test
    void appliesFixedDefaultsAndCacheValidation() {
        S3FileGdbOptions defaults = S3FileGdbOptions.defaults();

        assertEquals(1024 * 1024, defaults.blockSizeBytes());
        assertEquals(64L * 1024 * 1024, defaults.maxCacheBytes());
        assertEquals(
                new S3FileGdbOptions(64 * 1024, 2L * 64 * 1024),
                new S3FileGdbOptions(64 * 1024, 2L * 64 * 1024));

        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbOptions(32 * 1024, 64 * 1024));
        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbOptions(96 * 1024, 192 * 1024));
        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbOptions(16 * 1024 * 1024, 16 * 1024 * 1024));
        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbOptions(64 * 1024, 32 * 1024));
        assertThrows(IllegalArgumentException.class, () -> new S3FileGdbOptions(64 * 1024, 96 * 1024));
    }
}

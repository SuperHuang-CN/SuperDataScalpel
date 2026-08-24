package cn.superhuang.datascalpel.taskengine.runner;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.io.ParseException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunnerLogSanitizerTest {

    @Test
    void redactsCredentialsTokensUserInfoAndPresignedUrls() {
        RuntimeException failure = new RuntimeException(
                "password=hunter2 token:abc123 credential=visible "
                        + "https://alice:secret@example.test/file "
                        + "https://minio.test/file?X-Amz-Credential=user&X-Amz-Signature=signed");

        String stack = RunnerLogSanitizer.stackTrace(failure);

        assertFalse(stack.contains("hunter2"));
        assertFalse(stack.contains("abc123"));
        assertFalse(stack.contains("credential=visible"));
        assertFalse(stack.contains("alice:secret"));
        assertFalse(stack.contains("X-Amz-Signature"));
        assertTrue(stack.contains("password=***"));
        assertTrue(stack.contains("[redacted-presigned-url]"));
    }

    @Test
    void redactsPrivateFileLocationsAndExecutorTemporaryPaths() {
        RuntimeException failure = new RuntimeException(
                "s3a://private-bucket/root/orders/part-000.parquet "
                        + "objectKey=root/orders/source.parquet "
                        + "materializedPrefix=root/materialized/abc "
                        + "/tmp/datascalpel-file-input-123456.xlsx "
                        + "/private/tmp/datascalpel-geojson-123456/districts.geojson "
                        + "/var/tmp/datascalpel-shapefile-654321/districts.shp "
                        + "/private/var/folders/cache/spark-a3f3c1/_temporary/part-000.parquet"
        );

        String stack = RunnerLogSanitizer.stackTrace(failure);

        assertFalse(stack.contains("part-000.parquet"));
        assertFalse(stack.contains("root/orders/source.parquet"));
        assertFalse(stack.contains("root/materialized/abc"));
        assertFalse(stack.contains("datascalpel-file-input-123456.xlsx"));
        assertFalse(stack.contains("datascalpel-geojson-123456"));
        assertFalse(stack.contains("datascalpel-shapefile-654321"));
        assertFalse(stack.contains("spark-a3f3c1"));
        assertTrue(stack.contains("[redacted-object-uri]"));
        assertTrue(stack.contains("[redacted-file-input-temp-path]"));
        assertTrue(stack.contains("[redacted-file-output-temp-path]"));
        assertTrue(stack.contains("[redacted-spark-temp-path]"));
    }

    @Test
    void truncatesSanitizedStackAtSixtyFourKib() {
        String large = "x".repeat(100_000) + " password=never-visible";
        String stack = RunnerLogSanitizer.stackTrace(new IllegalStateException(large));

        assertTrue(stack.endsWith("...[STACK_TRUNCATED_AT_64_KIB]"));
        assertTrue(stack.getBytes(StandardCharsets.UTF_8).length <= RunnerLogSanitizer.MAX_STACK_BYTES);
        assertFalse(stack.contains("never-visible"));
    }

    @Test
    void removesSpatialExceptionMessagesButKeepsDiagnosticStackFrames() {
        RuntimeException failure = new RuntimeException(
                "outer message includes POLYGON ((secret coordinates))",
                new ParseException("inner message includes secret malformed WKT")
        );

        String stack = RunnerLogSanitizer.spatialSafeStackTrace(failure);

        assertFalse(stack.contains("secret coordinates"));
        assertFalse(stack.contains("secret malformed WKT"));
        assertTrue(stack.contains("[spatial-message-redacted]"));
        assertTrue(stack.contains("RunnerLogSanitizerTest"));
        assertTrue(stack.contains(ParseException.class.getName()));
    }

    @Test
    void redactsConfiguredJdbcReadOptionValuesBeforeStackTruncation() {
        String statement = "SET statement_timeout = '10min'";
        RuntimeException failure = new RuntimeException("JDBC rejected " + statement + " " + "x".repeat(100_000));

        String stack = RunnerLogSanitizer.jdbcReadOptionSafeStackTrace(failure, List.of(statement));

        assertFalse(stack.contains(statement));
        assertTrue(stack.contains("[redacted-jdbc-read-option-value]"));
        assertTrue(stack.endsWith("...[STACK_TRUNCATED_AT_64_KIB]"));
    }
}

package cn.superhuang.datascalpel.taskengine.runner;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

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
                        + "/tmp/datascalpel-file-input-123456.xlsx"
        );

        String stack = RunnerLogSanitizer.stackTrace(failure);

        assertFalse(stack.contains("part-000.parquet"));
        assertFalse(stack.contains("root/orders/source.parquet"));
        assertFalse(stack.contains("root/materialized/abc"));
        assertFalse(stack.contains("datascalpel-file-input-123456.xlsx"));
        assertTrue(stack.contains("[redacted-object-uri]"));
        assertTrue(stack.contains("[redacted-file-input-temp-path]"));
    }

    @Test
    void truncatesSanitizedStackAtSixtyFourKib() {
        String large = "x".repeat(100_000) + " password=never-visible";
        String stack = RunnerLogSanitizer.stackTrace(new IllegalStateException(large));

        assertTrue(stack.endsWith("...[STACK_TRUNCATED_AT_64_KIB]"));
        assertTrue(stack.getBytes(StandardCharsets.UTF_8).length <= RunnerLogSanitizer.MAX_STACK_BYTES);
        assertFalse(stack.contains("never-visible"));
    }
}

package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingException;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingInfrastructureException;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageException;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageObjectNotFoundException;
import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileDatasetParseFailureClassifierTest {

    private final FileDatasetParseFailureClassifier classifier = new FileDatasetParseFailureClassifier();

    @Test
    void reportsGdbFormatCodeAndBoundedDiagnosticWithoutRetrying() {
        var failure = classifier.classify(new FileGdbException(
                FileGdbErrorCode.UNSUPPORTED_FORMAT, "Field category has unsupported type code 13\n" + "x".repeat(400)));
        assertFalse(failure.retryable());
        assertTrue(failure.message().startsWith("GDB 包含暂不支持的格式（UNSUPPORTED_FORMAT）："));
        assertTrue(failure.message().contains("type code 13"));
        assertFalse(failure.message().contains("\n"));
        assertTrue(failure.message().length() < 360);
        assertTrue(classifier.classify(new FileGdbException(
                FileGdbErrorCode.MALFORMED_HEADER, "a00000009 schema has an invalid field-section terminator"))
                .message().contains("MALFORMED_HEADER"));
    }

    @Test
    void hidesGdbStorageDetailsAndPreservesRetrySemantics() {
        for (var code : new FileGdbErrorCode[]{FileGdbErrorCode.IO_ERROR, FileGdbErrorCode.SOURCE_CHANGED}) {
            var failure = classifier.classify(new FileGdbException(code, "AccessKey=secret /private/bucket"));
            assertTrue(failure.retryable());
            assertEquals("GDB 对象读取暂时失败", failure.message());
        }
        var missing = classifier.classify(new FileGdbException(FileGdbErrorCode.MISSING_FILE, "/private/bucket"));
        assertFalse(missing.retryable());
        assertEquals("GDB 缺少必要组件（MISSING_FILE）", missing.message());
    }

    @Test
    void retriesOnlyExplicitInfrastructureFailures() {
        assertTrue(classifier.classify(new FileStorageException("供应商详情", null)).retryable());
        assertTrue(classifier.classify(
                new FileDatasetParsingInfrastructureException("文件对象存储尚未配置")
        ).retryable());

        assertFalse(classifier.classify(
                new FileStorageObjectNotFoundException("对象不存在", null)
        ).retryable());
        assertFalse(classifier.classify(new FileDatasetParsingException("CSV 列数不一致")).retryable());
        assertFalse(classifier.classify(new IOException("压缩内容损坏")).retryable());
        assertFalse(classifier.classify(new IllegalStateException("程序错误")).retryable());
    }

    @Test
    void storesStableMessagesWithoutProviderDetails() {
        assertEquals(
                "文件对象存储暂时不可用",
                classifier.classify(new FileStorageException("AccessKey=secret", null)).message()
        );
        assertEquals(
                "文件内容不存在",
                classifier.classify(new FileStorageObjectNotFoundException("bucket/key", null)).message()
        );
        assertEquals(
                "文件解析发生内部错误",
                classifier.classify(new IllegalStateException("database-password" )).message()
        );
    }
}

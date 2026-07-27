package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingException;
import cn.superhuang.data.scalpel.business.filedataset.service.parse.FileDatasetParsingInfrastructureException;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageException;
import cn.superhuang.data.scalpel.business.filedataset.storage.FileStorageObjectNotFoundException;
import cn.superhuang.data.scalpel.filegdb.FileGdbErrorCode;
import cn.superhuang.data.scalpel.filegdb.FileGdbException;
import cn.superhuang.data.scalpel.shapefile.ShapefileErrorCode;
import cn.superhuang.data.scalpel.shapefile.ShapefileException;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** Maps parser failures to stable queue retry semantics without persisting provider details. */
@Component
public class FileDatasetParseFailureClassifier {

    public Failure classify(Throwable exception) {
        if (exception instanceof FileStorageObjectNotFoundException) {
            return new Failure(false, "文件内容不存在");
        }
        if (exception instanceof FileStorageException) {
            return new Failure(true, "文件对象存储暂时不可用");
        }
        if (exception instanceof FileDatasetParsingInfrastructureException infrastructureException) {
            return new Failure(true, safeMessage(infrastructureException, "文件解析基础设施暂时不可用"));
        }
        if (exception instanceof FileDatasetParsingException parsingException) {
            return new Failure(false, safeMessage(parsingException, "文件内容或解析参数无效"));
        }
        if (exception instanceof FileGdbException fileGdbException) {
            boolean retryable = fileGdbException.code() == FileGdbErrorCode.IO_ERROR
                    || fileGdbException.code() == FileGdbErrorCode.SOURCE_CHANGED;
            return new Failure(
                    retryable,
                    retryable ? "GDB 对象读取暂时失败" : "GDB 目录内容无效或当前读取器不支持"
            );
        }
        if (exception instanceof ShapefileException shapefileException) {
            boolean retryable = shapefileException.errorCode() == ShapefileErrorCode.IO_ERROR
                    || shapefileException.errorCode() == ShapefileErrorCode.SOURCE_CHANGED;
            return new Failure(
                    retryable,
                    retryable
                            ? "SHP 组件读取暂时失败"
                            : safeMessage(shapefileException, "SHP 组件内容无效或当前读取器不支持")
            );
        }
        if (exception instanceof IOException) {
            return new Failure(false, "文件内容无法按当前格式解析");
        }
        return new Failure(false, "文件解析发生内部错误");
    }

    private static String safeMessage(Throwable exception, String fallback) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? fallback : message.trim();
    }

    public record Failure(boolean retryable, String message) {
    }
}

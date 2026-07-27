package cn.superhuang.data.scalpel.filegdb;

import java.util.Objects;

/** Exception raised for an invalid, unsupported or unreadable File Geodatabase. */
public final class FileGdbException extends RuntimeException {
    private final FileGdbErrorCode code;

    public FileGdbException(FileGdbErrorCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
    }

    public FileGdbException(FileGdbErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = Objects.requireNonNull(code, "code");
    }

    public FileGdbErrorCode code() {
        return code;
    }
}

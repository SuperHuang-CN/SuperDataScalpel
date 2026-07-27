package cn.superhuang.data.scalpel.filegdb;

/** Stable categories for failures reported by the FileGDB reader. */
public enum FileGdbErrorCode {
    INVALID_DIRECTORY,
    MISSING_FILE,
    UNSUPPORTED_FORMAT,
    MALFORMED_HEADER,
    INVALID_OFFSET,
    LIMIT_EXCEEDED,
    TRUNCATED_INPUT,
    SOURCE_CHANGED,
    IO_ERROR,
    CLOSED
}

package cn.superhuang.data.scalpel.shapefile;

/** Stable categories for failures reported by the Shapefile reader. */
public enum ShapefileErrorCode {
    INVALID_SOURCE,
    MISSING_COMPONENT,
    UNSUPPORTED_FORMAT,
    MALFORMED_HEADER,
    RECORD_MISMATCH,
    INVALID_OFFSET,
    INVALID_ENCODING,
    LIMIT_EXCEEDED,
    TRUNCATED_INPUT,
    SOURCE_CHANGED,
    IO_ERROR,
    CLOSED
}

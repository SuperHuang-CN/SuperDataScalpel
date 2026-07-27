package cn.superhuang.data.scalpel.shapefile;

import java.util.Objects;

/** Runtime failure with a stable category and a safe diagnostic message. */
public final class ShapefileException extends RuntimeException {
    private final ShapefileErrorCode errorCode;

    public ShapefileException(ShapefileErrorCode errorCode, String message) {
        super(message);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ShapefileException(ShapefileErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    public ShapefileErrorCode errorCode() {
        return errorCode;
    }
}

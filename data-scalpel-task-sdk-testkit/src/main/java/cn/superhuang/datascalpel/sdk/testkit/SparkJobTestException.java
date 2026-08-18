package cn.superhuang.datascalpel.sdk.testkit;

/** A deterministic TestKit configuration or simulated execution failure. */
public final class SparkJobTestException extends RuntimeException {
    private final String code;

    public SparkJobTestException(String code, String message) {
        super(message);
        this.code = required(code);
    }

    public SparkJobTestException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = required(code);
    }

    public String code() {
        return code;
    }

    private static String required(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("TestKit error code must not be blank");
        }
        return value;
    }
}

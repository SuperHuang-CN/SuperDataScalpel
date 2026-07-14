package cn.superhuang.data.scalpel.dialect.runtime;

public final class DatabaseAccessException extends RuntimeException {

    private final String code;

    public DatabaseAccessException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}

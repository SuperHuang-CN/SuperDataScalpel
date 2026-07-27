package cn.superhuang.data.scalpel.dispatcher.backend;

public class BackendException extends Exception {
    private final String code;

    public BackendException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BackendException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() { return code; }
}

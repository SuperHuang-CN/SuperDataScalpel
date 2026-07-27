package cn.superhuang.datascalpel.taskengine.httpapi;

public class HttpApiPullException extends RuntimeException {
    private final String code;
    private final Integer httpStatus;

    public HttpApiPullException(String code, String message) {
        this(code, message, null, null);
    }

    public HttpApiPullException(String code, String message, Integer httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() {
        return code;
    }

    public Integer httpStatus() {
        return httpStatus;
    }
}

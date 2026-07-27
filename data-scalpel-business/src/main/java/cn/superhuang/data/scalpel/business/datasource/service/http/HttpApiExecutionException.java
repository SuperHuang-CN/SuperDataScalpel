package cn.superhuang.data.scalpel.business.datasource.service.http;

public class HttpApiExecutionException extends RuntimeException {

    private final String code;
    private final Integer httpStatus;
    private final String responsePreview;

    public HttpApiExecutionException(String code, String message) {
        this(code, message, null, null, null);
    }

    public HttpApiExecutionException(
            String code,
            String message,
            Integer httpStatus,
            String responsePreview,
            Throwable cause
    ) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
        this.responsePreview = responsePreview;
    }

    public String code() {
        return code;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public String responsePreview() {
        return responsePreview;
    }
}

package cn.superhuang.data.scalpel.web.error;

import org.springframework.http.HttpStatusCode;

/** Allows a business boundary to expose one stable RFC 9457 problem code. */
public class CodedProblemException extends RuntimeException {

    private final HttpStatusCode status;
    private final String code;

    public CodedProblemException(HttpStatusCode status, String code, String detail) {
        super(detail);
        if (status == null || code == null || code.isBlank() || detail == null || detail.isBlank()) {
            throw new IllegalArgumentException("问题状态、编码和说明不能为空");
        }
        this.status = status;
        this.code = code.trim();
    }

    public HttpStatusCode status() {
        return status;
    }

    public String code() {
        return code;
    }
}

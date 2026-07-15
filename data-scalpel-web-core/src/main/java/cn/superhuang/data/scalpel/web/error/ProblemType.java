package cn.superhuang.data.scalpel.web.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.net.URI;
import java.util.Locale;

/** Stable problem categories exposed by DataScalpel HTTP APIs. */
public enum ProblemType {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "请求参数校验失败"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "请求内容无效"),
    INVALID_SEARCH_REQUEST(HttpStatus.BAD_REQUEST, "INVALID_SEARCH_REQUEST", "查询条件无效"),
    INVALID_QUERY(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "查询请求无效"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "请求无效"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "需要身份认证"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "无权访问"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "资源不存在"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "请求方法不支持"),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "请求格式不可接受"),
    BUSINESS_CONFLICT(HttpStatus.CONFLICT, "BUSINESS_CONFLICT", "资源状态冲突"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "请求内容过大"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "媒体类型不支持"),
    NOT_IMPLEMENTED(HttpStatus.NOT_IMPLEMENTED, "NOT_IMPLEMENTED", "功能暂未实现"),
    UPSTREAM_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "UPSTREAM_UNAVAILABLE", "上游服务不可用"),
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "服务暂不可用"),
    UPSTREAM_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "UPSTREAM_TIMEOUT", "上游服务响应超时"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "系统内部错误");

    private final HttpStatus defaultStatus;
    private final String code;
    private final String title;
    private final URI type;

    ProblemType(HttpStatus defaultStatus, String code, String title) {
        this.defaultStatus = defaultStatus;
        this.code = code;
        this.title = title;
        this.type = URI.create("urn:datascalpel:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-'));
    }

    public HttpStatus defaultStatus() {
        return defaultStatus;
    }

    public String code() {
        return code;
    }

    public String title() {
        return title;
    }

    public URI type() {
        return type;
    }

    public static ProblemType fromStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> BAD_REQUEST;
            case 401 -> AUTHENTICATION_REQUIRED;
            case 403 -> ACCESS_DENIED;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> BUSINESS_CONFLICT;
            case 413 -> PAYLOAD_TOO_LARGE;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            case 501 -> NOT_IMPLEMENTED;
            case 502 -> UPSTREAM_UNAVAILABLE;
            case 503 -> SERVICE_UNAVAILABLE;
            case 504 -> UPSTREAM_TIMEOUT;
            default -> status.is4xxClientError() ? BAD_REQUEST : INTERNAL_ERROR;
        };
    }
}

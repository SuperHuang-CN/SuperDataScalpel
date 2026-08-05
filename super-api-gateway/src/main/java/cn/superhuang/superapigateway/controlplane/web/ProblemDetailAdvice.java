package cn.superhuang.superapigateway.controlplane.web;

import cn.superhuang.superapigateway.controlplane.service.ResourceConflictException;
import cn.superhuang.superapigateway.controlplane.service.InvalidManagementRequestException;
import cn.superhuang.superapigateway.controlplane.service.ManagementAuthenticationException;
import cn.superhuang.superapigateway.controlplane.service.ResourceNotFoundException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestControllerAdvice(basePackages = "cn.superhuang.superapigateway.controlplane.web.resource")
public class ProblemDetailAdvice {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAdvice.class);

    @ExceptionHandler(ManagementAuthenticationException.class)
    ProblemDetail authentication(
            ManagementAuthenticationException exception,
            ServerWebExchange exchange
    ) {
        return problem(HttpStatus.UNAUTHORIZED, "MANAGEMENT_AUTHENTICATION_FAILED",
                exception.getMessage(), exchange);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException exception, ServerWebExchange exchange) {
        return problem(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", exception.getMessage(), exchange);
    }

    @ExceptionHandler(InvalidManagementRequestException.class)
    ProblemDetail invalidRequest(
            InvalidManagementRequestException exception,
            ServerWebExchange exchange
    ) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", exception.getMessage(), exchange);
    }

    @ExceptionHandler({ResourceConflictException.class, DataIntegrityViolationException.class})
    ProblemDetail conflict(Exception exception, ServerWebExchange exchange) {
        String detail = exception instanceof ResourceConflictException
                ? exception.getMessage() : "资源配置与现有数据冲突";
        return problem(HttpStatus.CONFLICT, "RESOURCE_CONFLICT", detail, exchange);
    }

    @ExceptionHandler(WebExchangeBindException.class)
    ProblemDetail validation(WebExchangeBindException exception, ServerWebExchange exchange) {
        ProblemDetail detail = problem(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                "请求字段校验失败",
                exchange
        );
        List<Map<String, String>> violations = exception.getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()
                ))
                .toList();
        detail.setProperty("violations", violations);
        return detail;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraint(ConstraintViolationException exception, ServerWebExchange exchange) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", exception.getMessage(), exchange);
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception, ServerWebExchange exchange) {
        log.error("Unexpected management API failure for {}",
                exchange.getRequest().getPath().value(), exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "INTERNAL_ERROR",
                "网关管理操作失败",
                exchange
        );
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String code,
            String detail,
            ServerWebExchange exchange
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:super-api-gateway:problem:" + code.toLowerCase()));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(exchange.getRequest().getPath().value()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}

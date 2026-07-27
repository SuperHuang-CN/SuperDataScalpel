package cn.superhuang.data.scalpel.dispatcher.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Instant;

@RestControllerAdvice
public class DispatcherExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(DispatcherExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail status(ResponseStatusException exception, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(exception.getStatusCode(), exception.getReason());
        enrich(detail, request, code(exception.getStatusCode().value()));
        return detail;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "请求字段校验失败");
        enrich(detail, request, "VALIDATION_FAILED");
        detail.setProperty("violations", exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new Violation(error.getField(), error.getDefaultMessage())).toList());
        return detail;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unexpected Dispatcher error while handling {}", request.getRequestURI(), exception);
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Dispatcher 内部错误");
        enrich(detail, request, "INTERNAL_ERROR");
        return detail;
    }

    private static void enrich(ProblemDetail detail, HttpServletRequest request, String code) {
        detail.setTitle(detail.getStatus() == 401 ? "未授权" : "请求处理失败");
        detail.setType(URI.create("urn:datascalpel:task-dispatcher:problem:" + code.toLowerCase().replace('_', '-')));
        detail.setInstance(URI.create(request.getRequestURI()));
        detail.setProperty("code", code);
        detail.setProperty("timestamp", Instant.now());
    }

    private static String code(int status) {
        return switch (status) {
            case 401 -> "UNAUTHORIZED";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 429 -> "CAPACITY_EXCEEDED";
            case 503 -> "DEPENDENCY_UNAVAILABLE";
            default -> "REQUEST_REJECTED";
        };
    }

    private record Violation(String field, String message) { }
}

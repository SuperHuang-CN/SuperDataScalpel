package cn.superhuang.data.scalpel.web.error;

import cn.superhuang.data.scalpel.search.InvalidSearchRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ProblemDetailsExceptionHandler extends ResponseEntityExceptionHandler {

    private final ProblemDetailFactory problemDetailFactory;

    public ProblemDetailsExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler(InvalidSearchRequestException.class)
    ProblemDetail handleInvalidSearchRequest(InvalidSearchRequestException exception, HttpServletRequest request) {
        return problemDetailFactory.create(ProblemType.INVALID_SEARCH_REQUEST, exception.getMessage(), request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        ProblemDetail problem = problemDetailFactory.create(ProblemType.VALIDATION_FAILED, "请求参数校验失败", request);
        problem.setProperty("violations", exception.getConstraintViolations().stream()
                .map(violation -> violation(violation.getPropertyPath().toString(), violation.getMessage()))
                .toList());
        return problem;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleDataIntegrityViolation(DataIntegrityViolationException exception, HttpServletRequest request) {
        return problemDetailFactory.create(ProblemType.BUSINESS_CONFLICT, "数据操作与当前资源状态冲突", request);
    }

    @ExceptionHandler(CodedProblemException.class)
    ProblemDetail handleCodedProblem(CodedProblemException exception, HttpServletRequest request) {
        ProblemDetail problem = problemDetailFactory.create(
                ProblemType.fromStatus(exception.status()), exception.status(), exception.getMessage(), request);
        problem.setProperty("code", exception.code());
        return problem;
    }

    @ExceptionHandler(AuthenticationException.class)
    ProblemDetail handleAuthentication(AuthenticationException exception, HttpServletRequest request) {
        return problemDetailFactory.create(ProblemType.AUTHENTICATION_REQUIRED, "身份认证失败", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail handleAccessDenied(AccessDeniedException exception, HttpServletRequest request) {
        return problemDetailFactory.create(ProblemType.ACCESS_DENIED, "当前账号无权访问该资源", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception exception, HttpServletRequest request) {
        logger.error("Unhandled API exception on " + request.getRequestURI(), exception);
        ProblemDetail problem = problemDetailFactory.create(ProblemType.INTERNAL_ERROR, "系统内部错误", request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<Map<String, String>> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> violation(error.getField(), defaultMessage(error.getDefaultMessage())))
                .toList();
        ProblemDetail problem = problemDetailFactory.create(ProblemType.VALIDATION_FAILED, status, "请求参数校验失败");
        problem.setProperty("violations", violations);
        return createResponseEntity(problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        List<Map<String, String>> violations = new ArrayList<>();
        exception.getParameterValidationResults().forEach(result -> result.getResolvableErrors().forEach(error ->
                violations.add(violation(parameterName(result.getMethodParameter().getParameterName()), defaultMessage(error.getDefaultMessage())))));
        ProblemDetail problem = problemDetailFactory.create(ProblemType.VALIDATION_FAILED, status, "请求参数校验失败");
        problem.setProperty("violations", violations);
        return createResponseEntity(problem, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return response(ProblemType.MALFORMED_REQUEST, status, "请求内容无法解析", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return response(ProblemType.BAD_REQUEST, status, "请求参数类型不正确", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return response(ProblemType.PAYLOAD_TOO_LARGE, status, "上传内容超过允许大小", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return response(ProblemType.METHOD_NOT_ALLOWED, status, "当前资源不支持该请求方法", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        return response(ProblemType.UNSUPPORTED_MEDIA_TYPE, status, "请求的媒体类型不受支持", headers, request);
    }

    @Override
    protected ResponseEntity<Object> handleErrorResponseException(
            ErrorResponseException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request
    ) {
        String detail = exception.getBody().getDetail();
        return response(ProblemType.fromStatus(status), status,
                detail == null || detail.isBlank() ? ProblemType.fromStatus(status).title() : detail,
                headers, request);
    }

    @Override
    protected ProblemDetail createProblemDetail(
            Exception exception,
            HttpStatusCode status,
            String defaultDetail,
            String detailMessageCode,
            Object[] detailMessageArguments,
            WebRequest request
    ) {
        String detail = defaultDetail == null || defaultDetail.isBlank()
                ? ProblemType.fromStatus(status).title()
                : defaultDetail;
        return problemDetailFactory.create(ProblemType.fromStatus(status), status, detail);
    }

    private ResponseEntity<Object> response(
            ProblemType type,
            HttpStatusCode status,
            String detail,
            HttpHeaders headers,
            WebRequest request
    ) {
        return createResponseEntity(problemDetailFactory.create(type, status, detail), headers, status, request);
    }

    private static Map<String, String> violation(String field, String message) {
        return Map.of("field", parameterName(field), "message", defaultMessage(message));
    }

    private static String parameterName(String value) {
        return value == null || value.isBlank() ? "request" : value;
    }

    private static String defaultMessage(String value) {
        return value == null || value.isBlank() ? "值不符合要求" : value;
    }
}

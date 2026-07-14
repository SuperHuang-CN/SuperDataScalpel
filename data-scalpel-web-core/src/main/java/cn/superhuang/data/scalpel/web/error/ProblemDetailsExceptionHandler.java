package cn.superhuang.data.scalpel.web.error;

import cn.superhuang.data.scalpel.search.InvalidSearchRequestException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class ProblemDetailsExceptionHandler {

    @ExceptionHandler(InvalidSearchRequestException.class)
    ProblemDetail handleInvalidSearchRequest(InvalidSearchRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        List<Map<String, String>> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() == null ? "invalid value" : error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Request validation failed");
        problem.setProperty("violations", exception.getConstraintViolations().stream()
                .map(violation -> Map.of(
                        "field", violation.getPropertyPath().toString(),
                        "message", violation.getMessage()))
                .toList());
        return problem;
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail handleResponseStatus(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.valueOf(exception.getStatusCode().value());
        String detail = exception.getReason() == null ? status.getReasonPhrase() : exception.getReason();
        return problem(status, detail);
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("timestamp", Instant.now());
        return problem;
    }
}

package cn.superhuang.data.scalpel.engine.query.web;

import cn.superhuang.data.scalpel.engine.query.EngineQueryValidationException;
import cn.superhuang.data.scalpel.web.error.ProblemDetailFactory;
import cn.superhuang.data.scalpel.web.error.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class EngineQueryExceptionHandler {

    private final ProblemDetailFactory problemDetailFactory;

    public EngineQueryExceptionHandler(ProblemDetailFactory problemDetailFactory) {
        this.problemDetailFactory = problemDetailFactory;
    }

    @ExceptionHandler(EngineQueryValidationException.class)
    ProblemDetail invalidQuery(EngineQueryValidationException exception, HttpServletRequest request) {
        return problemDetailFactory.create(ProblemType.INVALID_QUERY, exception.getMessage(), request);
    }
}

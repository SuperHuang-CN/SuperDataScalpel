package cn.superhuang.data.scalpel.web.error;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

import java.net.URI;
import java.time.Instant;

/** Creates a single RFC 9457 representation for all DataScalpel API failures. */
public class ProblemDetailFactory {

    public ProblemDetail create(ProblemType type, String detail) {
        return create(type, type.defaultStatus(), detail, null);
    }

    public ProblemDetail create(ProblemType type, String detail, HttpServletRequest request) {
        return create(type, type.defaultStatus(), detail, request);
    }

    public ProblemDetail create(ProblemType type, HttpStatusCode status, String detail) {
        return create(type, status, detail, null);
    }

    public ProblemDetail create(ProblemType type, HttpStatusCode status, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(type.type());
        problem.setTitle(type.title());
        problem.setProperty("code", type.code());
        problem.setProperty("timestamp", Instant.now());
        if (request != null) {
            problem.setInstance(URI.create(request.getRequestURI()));
        }
        return problem;
    }
}

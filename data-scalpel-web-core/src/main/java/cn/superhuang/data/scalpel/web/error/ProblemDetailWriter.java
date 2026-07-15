package cn.superhuang.data.scalpel.web.error;

import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Writes the shared problem format from security filters that run outside MVC advice. */
public class ProblemDetailWriter {

    private final ObjectMapper objectMapper;
    private final ProblemDetailFactory problemDetailFactory;

    public ProblemDetailWriter(ObjectMapper objectMapper, ProblemDetailFactory problemDetailFactory) {
        this.objectMapper = objectMapper;
        this.problemDetailFactory = problemDetailFactory;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ProblemType type, String detail)
            throws IOException {
        response.setStatus(type.defaultStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problemDetailFactory.create(type, detail, request));
    }
}

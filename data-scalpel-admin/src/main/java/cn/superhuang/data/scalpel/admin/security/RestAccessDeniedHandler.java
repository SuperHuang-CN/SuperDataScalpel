package cn.superhuang.data.scalpel.admin.security;

import cn.superhuang.data.scalpel.web.error.ProblemDetailWriter;
import cn.superhuang.data.scalpel.web.error.ProblemType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailWriter problemDetailWriter;

    public RestAccessDeniedHandler(ProblemDetailWriter problemDetailWriter) {
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException, ServletException {
        problemDetailWriter.write(request, response, ProblemType.ACCESS_DENIED, "当前账号无权访问该资源");
    }
}

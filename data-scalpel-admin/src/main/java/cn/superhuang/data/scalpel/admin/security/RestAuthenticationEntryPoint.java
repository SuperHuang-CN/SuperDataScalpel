package cn.superhuang.data.scalpel.admin.security;

import cn.superhuang.data.scalpel.web.error.ProblemDetailWriter;
import cn.superhuang.data.scalpel.web.error.ProblemType;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailWriter problemDetailWriter;

    public RestAuthenticationEntryPoint(ProblemDetailWriter problemDetailWriter) {
        this.problemDetailWriter = problemDetailWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authenticationException) throws IOException, ServletException {
        problemDetailWriter.write(request, response, ProblemType.AUTHENTICATION_REQUIRED, "需要先登录后才能访问该资源");
    }
}

package cn.superhuang.data.scalpel.dispatcher.web;

import cn.superhuang.data.scalpel.dispatcher.config.DispatcherProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class DispatcherTokenInterceptor implements HandlerInterceptor {
    private final DispatcherProperties properties;

    public DispatcherTokenInterceptor(DispatcherProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        String presented = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : "";
        String expected = properties.token() == null ? "" : properties.token();
        if (!MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Dispatcher 访问令牌无效");
        }
        return true;
    }
}

package cn.superhuang.superapigateway.controlplane.security;

import cn.superhuang.superapigateway.configuration.SuperApiGatewayProperties;
import cn.superhuang.superapigateway.controlplane.web.ProblemResponseWriter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminAuthenticationWebFilter implements WebFilter {

    public static final String MACHINE_TOKEN_HEADER = "X-Super-Gateway-Admin-Token";

    private final SuperApiGatewayProperties.Admin properties;
    private final JwtTokenService jwtTokens;
    private final ProblemResponseWriter problems;

    public AdminAuthenticationWebFilter(
            SuperApiGatewayProperties properties,
            JwtTokenService jwtTokens,
            ProblemResponseWriter problems
    ) {
        this.properties = properties.admin();
        this.jwtTokens = jwtTokens;
        this.problems = problems;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().value();
        if (HttpMethod.OPTIONS.equals(exchange.getRequest().getMethod())
                || "/admin-api/v1/auth/login".equals(path)
                || "/actuator/health".equals(path)
                || "/actuator/info".equals(path)
                || !isProtected(path)) {
            return chain.filter(exchange);
        }

        String machineToken = exchange.getRequest().getHeaders().getFirst(MACHINE_TOKEN_HEADER);
        if (constantTimeEquals(machineToken, properties.machineToken())) {
            return chain.filter(exchange);
        }

        String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")
                && jwtTokens.validate(authorization.substring(7))) {
            return chain.filter(exchange);
        }

        exchange.getResponse().getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return problems.write(
                exchange,
                HttpStatus.UNAUTHORIZED,
                "MANAGEMENT_AUTHENTICATION_REQUIRED",
                "Authentication required",
                "A valid management JWT or machine token is required"
        );
    }

    private static boolean isProtected(String path) {
        return path.startsWith("/admin-api/") || path.startsWith("/actuator/");
    }

    private static boolean constantTimeEquals(String actual, String expected) {
        if (actual == null || expected == null) return false;
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }
}

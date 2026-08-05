package cn.superhuang.superapigateway.controlplane.web.resource;

import cn.superhuang.superapigateway.configuration.SuperApiGatewayProperties;
import cn.superhuang.superapigateway.controlplane.execution.ControlPlaneExecutor;
import cn.superhuang.superapigateway.controlplane.security.JwtTokenService;
import cn.superhuang.superapigateway.controlplane.service.ManagementAuthenticationException;
import cn.superhuang.superapigateway.controlplane.web.request.LoginRequest;
import cn.superhuang.superapigateway.controlplane.web.response.AuthResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;

@RestController
@RequestMapping("/admin-api/v1/auth")
public class AuthResource {

    private final SuperApiGatewayProperties.Admin properties;
    private final JwtTokenService jwtTokens;
    private final ControlPlaneExecutor executor;

    public AuthResource(
            SuperApiGatewayProperties properties,
            JwtTokenService jwtTokens,
            ControlPlaneExecutor executor
    ) {
        this.properties = properties.admin();
        this.jwtTokens = jwtTokens;
        this.executor = executor;
    }

    @PostMapping("/login")
    public Mono<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return executor.execute(() -> {
            if (!constantTimeEquals(request.username(), properties.username())
                    || !constantTimeEquals(request.password(), properties.password())) {
                throw new ManagementAuthenticationException("用户名或密码错误");
            }
            return jwtTokens.issue(properties.username());
        });
    }

    @GetMapping("/me")
    public Mono<Map<String, String>> me() {
        return Mono.just(Map.of("username", properties.username()));
    }

    private static boolean constantTimeEquals(String actual, String expected) {
        return MessageDigest.isEqual(
                actual.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8)
        );
    }
}

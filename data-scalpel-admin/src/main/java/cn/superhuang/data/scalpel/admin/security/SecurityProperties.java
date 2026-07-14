package cn.superhuang.data.scalpel.admin.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "data-scalpel.security")
public record SecurityProperties(
        @Valid @NotNull Admin admin,
        @Valid @NotNull Jwt jwt) {

    public record Admin(
            @NotBlank String username,
            @NotBlank String password) {
    }

    public record Jwt(
            @NotBlank String issuer,
            @NotBlank String secret,
            @NotNull Duration timeToLive) {
    }
}


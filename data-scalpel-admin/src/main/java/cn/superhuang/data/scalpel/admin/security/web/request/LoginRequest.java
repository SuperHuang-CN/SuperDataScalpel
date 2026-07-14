package cn.superhuang.data.scalpel.admin.security.web.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(@NotBlank String username, @NotBlank String password) {
}

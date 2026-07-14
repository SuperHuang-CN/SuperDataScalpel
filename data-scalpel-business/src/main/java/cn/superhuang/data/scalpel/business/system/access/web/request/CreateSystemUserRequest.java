package cn.superhuang.data.scalpel.business.system.access.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateSystemUserRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]{2,63}", message = "用户名以字母开头，只能包含字母、数字、点、下划线和连字符")
        String username,
        @NotBlank @Size(max = 100) String displayName,
        @NotBlank @Size(min = 8, max = 128) String password,
        @NotNull UUID roleId,
        Boolean enabled
) {
}

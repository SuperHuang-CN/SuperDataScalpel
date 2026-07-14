package cn.superhuang.data.scalpel.business.system.access.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateSystemUserRequest(
        @NotBlank @Size(max = 100) String displayName,
        @NotNull UUID roleId,
        @NotNull Boolean enabled
) {
}

package cn.superhuang.data.scalpel.business.system.access.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateSystemRoleRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {
}

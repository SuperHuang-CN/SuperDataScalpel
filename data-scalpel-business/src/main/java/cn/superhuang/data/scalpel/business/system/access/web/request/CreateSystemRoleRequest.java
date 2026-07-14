package cn.superhuang.data.scalpel.business.system.access.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSystemRoleRequest(
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]{0,63}", message = "角色编码以字母开头，只能包含字母、数字、点、下划线和连字符")
        String code,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 500) String description
) {
}

package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateServiceEngineRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Size(max = 500) String adminUrl,
        @NotBlank @Size(max = 500) String publicUrl,
        Boolean enabled,
        @Size(max = 1000) String description
) {
}

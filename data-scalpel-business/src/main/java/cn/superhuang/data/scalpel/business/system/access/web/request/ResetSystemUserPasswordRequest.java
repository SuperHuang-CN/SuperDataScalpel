package cn.superhuang.data.scalpel.business.system.access.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetSystemUserPasswordRequest(
        @NotBlank @Size(min = 8, max = 128) String password
) {
}

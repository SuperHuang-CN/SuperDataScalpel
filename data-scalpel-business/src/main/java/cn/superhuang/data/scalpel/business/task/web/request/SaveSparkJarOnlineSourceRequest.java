package cn.superhuang.data.scalpel.business.task.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveSparkJarOnlineSourceRequest(
        @NotBlank @Size(max = 262_144) String sourceCode
) {
}

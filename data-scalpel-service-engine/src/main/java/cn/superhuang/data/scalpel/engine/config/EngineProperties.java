package cn.superhuang.data.scalpel.engine.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Mandatory local configuration of one independently deployed Engine process. */
@Validated
@ConfigurationProperties(prefix = "data-scalpel.engine")
public record EngineProperties(
        @NotBlank
        @Pattern(
                regexp = "[A-Za-z][A-Za-z0-9_]{0,63}",
                message = "必须以字母开头，仅支持字母、数字和下划线，最长 64 位"
        )
        String code,
        @NotBlank String managementToken
) {
}

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
        @Pattern(regexp = "^(?!\\$\\{).+$", message = "必须配置为实际 Engine 编码，不能保留未解析占位符")
        String code,
        @NotBlank String managementToken,
        @NotBlank String encryptionKey
) {
}

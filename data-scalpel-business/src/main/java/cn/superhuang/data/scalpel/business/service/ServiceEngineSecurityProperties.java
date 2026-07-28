package cn.superhuang.data.scalpel.business.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "data-scalpel.service-engine")
public record ServiceEngineSecurityProperties(@NotBlank String credentialKey) {
}

package cn.superhuang.data.scalpel.business.service;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Shared fixed token for Admin to Engine control-plane calls in V1. */
@Validated
@ConfigurationProperties(prefix = "data-scalpel.service-engine")
public record ServiceEngineManagementProperties(@NotBlank String managementToken) {
}

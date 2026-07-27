package cn.superhuang.data.scalpel.business.compute.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "data-scalpel.compute-engine")
public record ComputeEngineProperties(
        String credentialKey,
        Duration connectTimeout,
        Duration requestTimeout
) {
    public ComputeEngineProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
    }
}

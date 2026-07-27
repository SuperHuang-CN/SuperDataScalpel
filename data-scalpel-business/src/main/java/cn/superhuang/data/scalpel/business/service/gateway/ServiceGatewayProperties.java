package cn.superhuang.data.scalpel.business.service.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "data-scalpel.service-gateway")
public record ServiceGatewayProperties(
        GatewayProvider provider,
        Kong kong
) {

    public ServiceGatewayProperties {
        provider = provider == null ? GatewayProvider.NONE : provider;
        kong = kong == null ? new Kong(null, null, null, null) : kong;
    }

    public record Kong(
            String adminUrl,
            String proxyUrl,
            Duration connectTimeout,
            Duration requestTimeout
    ) {
        public Kong {
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
            requestTimeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
        }
    }
}

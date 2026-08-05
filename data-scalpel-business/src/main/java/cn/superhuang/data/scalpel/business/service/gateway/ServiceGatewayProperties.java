package cn.superhuang.data.scalpel.business.service.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.time.Duration;

@ConfigurationProperties(prefix = "data-scalpel.service-gateway")
public record ServiceGatewayProperties(
        GatewayProvider provider,
        Kong kong,
        SuperApiGateway superApiGateway
) {

    @ConstructorBinding
    public ServiceGatewayProperties {
        provider = provider == null ? GatewayProvider.NONE : provider;
        kong = kong == null ? new Kong(null, null, null, null) : kong;
        superApiGateway = superApiGateway == null
                ? new SuperApiGateway(null, null, null, null, null, null, null)
                : superApiGateway;
    }

    public ServiceGatewayProperties(GatewayProvider provider, Kong kong) {
        this(provider, kong, null);
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

    public record SuperApiGateway(
            String adminUrl,
            String proxyUrl,
            String machineToken,
            Duration connectTimeout,
            Duration requestTimeout,
            Duration upstreamConnectTimeout,
            Duration upstreamResponseTimeout
    ) {
        public SuperApiGateway {
            connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
            requestTimeout = requestTimeout == null ? Duration.ofSeconds(5) : requestTimeout;
            upstreamConnectTimeout = upstreamConnectTimeout == null
                    ? Duration.ofSeconds(3) : upstreamConnectTimeout;
            upstreamResponseTimeout = upstreamResponseTimeout == null
                    ? Duration.ofSeconds(35) : upstreamResponseTimeout;
        }
    }
}

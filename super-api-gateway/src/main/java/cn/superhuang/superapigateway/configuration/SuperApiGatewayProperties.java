package cn.superhuang.superapigateway.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("super-api-gateway")
public record SuperApiGatewayProperties(
        String publicBaseUrl,
        Admin admin,
        ControlPlane controlPlane,
        Runtime runtime,
        AccessLog accessLog
) {

    public SuperApiGatewayProperties {
        publicBaseUrl = text(publicBaseUrl, "http://localhost:19000");
        admin = admin == null ? new Admin(null, null, null, null, null, null) : admin;
        controlPlane = controlPlane == null ? new ControlPlane(null, null, null) : controlPlane;
        runtime = runtime == null ? new Runtime(null, null, null, null) : runtime;
        accessLog = accessLog == null ? new AccessLog(null, null, null) : accessLog;
    }

    public record Admin(
            String username,
            String password,
            String jwtSecret,
            String jwtIssuer,
            Duration jwtTtl,
            String machineToken
    ) {
        public Admin {
            username = text(username, "admin");
            password = text(password, "admin123456");
            jwtSecret = text(jwtSecret, "change-me-super-api-gateway-jwt-secret-32-bytes");
            jwtIssuer = text(jwtIssuer, "super-api-gateway");
            jwtTtl = jwtTtl == null ? Duration.ofHours(8) : jwtTtl;
            machineToken = text(machineToken, "change-me-super-api-gateway-machine-token");
        }
    }

    public record ControlPlane(Integer coreThreads, Integer maxThreads, Integer queueCapacity) {
        public ControlPlane {
            coreThreads = positive(coreThreads, 4);
            maxThreads = positive(maxThreads, 16);
            queueCapacity = positive(queueCapacity, 200);
            if (maxThreads < coreThreads) {
                maxThreads = coreThreads;
            }
        }
    }

    public record Runtime(
            Integer reloadThreads,
            Duration revisionPollInterval,
            Duration heartbeatInterval,
            Duration instanceStaleAfter
    ) {
        public Runtime {
            reloadThreads = positive(reloadThreads, 2);
            revisionPollInterval = revisionPollInterval == null ? Duration.ofSeconds(5) : revisionPollInterval;
            heartbeatInterval = heartbeatInterval == null ? Duration.ofSeconds(10) : heartbeatInterval;
            instanceStaleAfter = instanceStaleAfter == null
                    || instanceStaleAfter.isZero()
                    || instanceStaleAfter.isNegative()
                    ? Duration.ofSeconds(30) : instanceStaleAfter;
        }
    }

    public record AccessLog(Boolean enabled, String topic, Integer queueCapacity) {
        public AccessLog {
            enabled = enabled == null || enabled;
            topic = text(topic, "super-api-gateway.access-log.v1");
            queueCapacity = positive(queueCapacity, 10_000);
        }
    }

    private static String text(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static int positive(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }
}

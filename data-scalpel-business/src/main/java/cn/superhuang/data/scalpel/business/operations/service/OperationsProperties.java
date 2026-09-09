package cn.superhuang.data.scalpel.business.operations.service;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;

@ConfigurationProperties(prefix = "data-scalpel.operations")
public record OperationsProperties(String credentialKey, String publicBaseUrl, Duration observationStaleAfter,
                                   int incidentRetentionDays, int notificationRetentionDays,
                                   int deliveryRetentionDays, int signalRetentionDays) {
    public OperationsProperties {
        publicBaseUrl = publicBaseUrl == null ? "" : publicBaseUrl.replaceAll("/+$", "");
        observationStaleAfter = observationStaleAfter == null ? Duration.ofSeconds(90) : observationStaleAfter;
        if (observationStaleAfter.compareTo(Duration.ofSeconds(30)) < 0) throw new IllegalArgumentException("观测有效期至少 30 秒");
        incidentRetentionDays = incidentRetentionDays <= 0 ? 90 : incidentRetentionDays;
        notificationRetentionDays = notificationRetentionDays <= 0 ? 30 : notificationRetentionDays;
        deliveryRetentionDays = deliveryRetentionDays <= 0 ? 30 : deliveryRetentionDays;
        signalRetentionDays = signalRetentionDays <= 0 ? 7 : signalRetentionDays;
    }
}

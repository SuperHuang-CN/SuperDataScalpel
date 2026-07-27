package cn.superhuang.data.scalpel.dispatcher.config;

import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.time.Duration;

@ConfigurationProperties(prefix = "data-scalpel.dispatcher")
public record DispatcherProperties(
        String token,
        ExecutionBackendType backend,
        Duration admissionPollInterval,
        Duration observationPollInterval,
        Duration outboxPollInterval,
        Duration outboxClaimTimeout,
        Duration submissionUncertainGrace,
        Duration observationFailureGrace,
        Duration deactivationTimeout,
        Duration sendTimeout,
        DataSize logMaxBytes
) {
    public DispatcherProperties {
        backend = backend == null ? ExecutionBackendType.LOCAL_DOCKER : backend;
        admissionPollInterval = admissionPollInterval == null ? Duration.ofMillis(500) : admissionPollInterval;
        observationPollInterval = observationPollInterval == null ? Duration.ofSeconds(5) : observationPollInterval;
        outboxPollInterval = outboxPollInterval == null ? Duration.ofMillis(500) : outboxPollInterval;
        outboxClaimTimeout = outboxClaimTimeout == null ? Duration.ofMinutes(1) : outboxClaimTimeout;
        submissionUncertainGrace = submissionUncertainGrace == null ? Duration.ofMinutes(2) : submissionUncertainGrace;
        observationFailureGrace = observationFailureGrace == null ? Duration.ofMinutes(5) : observationFailureGrace;
        deactivationTimeout = deactivationTimeout == null ? Duration.ofSeconds(30) : deactivationTimeout;
        sendTimeout = sendTimeout == null ? Duration.ofSeconds(10) : sendTimeout;
        logMaxBytes = logMaxBytes == null ? DataSize.ofMegabytes(20) : logMaxBytes;
    }
}

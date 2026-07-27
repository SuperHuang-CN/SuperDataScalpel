package cn.superhuang.data.scalpel.business.filedataset.service.queue;

import cn.superhuang.data.scalpel.business.system.configuration.domain.SystemConfigurationDefinition;
import cn.superhuang.data.scalpel.business.system.configuration.service.SystemConfigurationService;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/** Exponential retry schedule using the current system setting and a bounded maximum delay. */
@Component
public class FileDatasetParseRetryPolicy {

    static final Duration MAX_DELAY = Duration.ofHours(24);

    private final SystemConfigurationService configurationService;

    public FileDatasetParseRetryPolicy(SystemConfigurationService configurationService) {
        this.configurationService = configurationService;
    }

    public Instant nextAvailableAt(Instant failedAt, int attemptCount) {
        return nextAvailableAt(failedAt, attemptCount, currentBaseDelaySeconds());
    }

    public int currentBaseDelaySeconds() {
        return configurationService.requireInteger(
                SystemConfigurationDefinition.FILE_DATASET_PARSING_RETRY_BASE_DELAY_SECONDS
        );
    }

    public Instant nextAvailableAt(Instant failedAt, int attemptCount, int baseDelaySeconds) {
        return failedAt.plus(delay(baseDelaySeconds, attemptCount));
    }

    static Duration delay(int baseDelaySeconds, int attemptCount) {
        if (baseDelaySeconds < 1) {
            throw new IllegalArgumentException("重试基础延时必须大于零");
        }
        if (attemptCount < 1) {
            throw new IllegalArgumentException("任务尝试次数必须大于零");
        }
        long multiplier = 1L << Math.min(attemptCount - 1, 30);
        long delaySeconds;
        try {
            delaySeconds = Math.multiplyExact((long) baseDelaySeconds, multiplier);
        } catch (ArithmeticException exception) {
            delaySeconds = MAX_DELAY.toSeconds();
        }
        return Duration.ofSeconds(Math.min(delaySeconds, MAX_DELAY.toSeconds()));
    }
}

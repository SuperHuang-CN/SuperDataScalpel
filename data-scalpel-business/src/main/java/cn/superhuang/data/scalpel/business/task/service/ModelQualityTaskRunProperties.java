package cn.superhuang.data.scalpel.business.task.service;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "data-scalpel.task-run.model-quality")
public record ModelQualityTaskRunProperties(@NotNull Duration timeout) {
}

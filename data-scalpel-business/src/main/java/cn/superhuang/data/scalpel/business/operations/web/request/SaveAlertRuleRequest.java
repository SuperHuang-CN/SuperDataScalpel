package cn.superhuang.data.scalpel.business.operations.web.request;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

public record SaveAlertRuleRequest(@NotNull AlertRuleType ruleType, UUID subjectId, boolean enabled,
                                   @NotNull AlertSeverity severity, @Min(0) @Max(2592000) int thresholdSeconds,
                                   @Min(0) @Max(86400) int cooldownSeconds,
                                   @NotNull @Size(max=100) List<@NotNull UUID> userIds,
                                   @NotNull @Size(max=30) List<@NotNull UUID> channelIds) {}

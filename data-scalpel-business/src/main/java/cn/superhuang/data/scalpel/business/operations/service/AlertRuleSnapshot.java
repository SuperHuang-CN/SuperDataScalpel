package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AlertRuleSnapshot(UUID id, long version, AlertRuleType type, boolean enabled, AlertSeverity severity,
                                int thresholdSeconds, int cooldownSeconds, List<UUID> userIds, List<UUID> channelIds,
                                Map<UUID, Long> channelVersions) {}

package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.operations.domain.*;
import java.util.List;
import java.util.UUID;
public record AlertRuleResponse(UUID id, AlertRuleType ruleType, UUID subjectId, String subjectName, boolean enabled,
                                AlertSeverity severity, int thresholdSeconds, int cooldownSeconds,
                                long configurationVersion, List<UUID> userIds, List<UUID> channelIds) {}

package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.operations.domain.*;
import java.time.Instant;
import java.util.UUID;
public record AlertIncidentResponse(UUID id, AlertRuleType ruleType, AlertSeverity severity, UUID subjectId,
    String subjectName, UUID runId, UUID engineId, boolean sourceExists, AlertHandlingStatus status,
    AlertConditionState conditionState, String summary, String errorCode, UUID diagnosticId,
    Instant occurredAt, Instant detectedAt, Instant lastObservedAt, Instant closedAt, String closeReason, Instant silencedUntil,
    AlertNotificationSummary notifications) {}

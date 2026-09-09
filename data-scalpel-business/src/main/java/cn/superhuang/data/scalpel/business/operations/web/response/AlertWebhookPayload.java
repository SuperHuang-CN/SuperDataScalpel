package cn.superhuang.data.scalpel.business.operations.web.response;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import java.time.Instant;
import java.util.UUID;

public record AlertWebhookPayload(int schemaVersion, UUID deliveryId, UUID incidentId, AlertEventType eventType,
                                  int sequence, AlertSeverity severity, AlertRuleType ruleType, String subjectType,
                                  UUID subjectId, UUID taskId, UUID runId, UUID engineId, String subjectName,
                                  Instant occurredAt, Instant detectedAt, String summary, String errorCode,
                                  UUID diagnosticId, String detailUrl) {}

package cn.superhuang.data.scalpel.business.operations.service;

import java.time.Instant;
import java.util.UUID;

public record AlertRunFact(UUID runId, UUID taskId, String taskName, UUID engineId, String summary,
                           String errorCode, UUID diagnosticId, Instant occurredAt, AlertRuleSnapshot rule) {}

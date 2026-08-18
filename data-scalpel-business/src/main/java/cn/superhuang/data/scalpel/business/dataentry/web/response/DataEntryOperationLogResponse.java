package cn.superhuang.data.scalpel.business.dataentry.web.response;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationLog;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationStatus;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationType;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record DataEntryOperationLogResponse(
        UUID id,
        UUID formId,
        UUID modelId,
        Integer modelSchemaVersion,
        DataEntryOperationType operationType,
        DataEntryOperationStatus status,
        String operatorUsername,
        int requestedCount,
        Integer affectedCount,
        String payloadSnapshot,
        Instant completedAt,
        String errorCode,
        String errorMessage,
        boolean manualVerificationRequired,
        Instant createdAt,
        Instant updatedAt
) {
    public static DataEntryOperationLogResponse summary(DataEntryOperationLog log) {
        return from(log, false);
    }

    public static DataEntryOperationLogResponse detail(DataEntryOperationLog log) {
        return from(log, true);
    }

    private static DataEntryOperationLogResponse from(DataEntryOperationLog log, boolean includePayload) {
        boolean stale = log.getStatus() == DataEntryOperationStatus.PROCESSING
                && log.getCreatedAt() != null
                && Duration.between(log.getCreatedAt(), Instant.now()).toMinutes() >= 5;
        boolean manualVerification = stale || log.getStatus() == DataEntryOperationStatus.PARTIALLY_SUCCEEDED;
        return new DataEntryOperationLogResponse(
                log.getId(), log.getFormId(), log.getModelId(), log.getModelSchemaVersion(), log.getOperationType(),
                log.getStatus(), log.getOperatorUsername(), log.getRequestedCount(), log.getAffectedCount(),
                includePayload ? log.getPayloadSnapshot() : null, log.getCompletedAt(), log.getErrorCode(),
                log.getErrorMessage(), manualVerification, log.getCreatedAt(), log.getUpdatedAt()
        );
    }
}

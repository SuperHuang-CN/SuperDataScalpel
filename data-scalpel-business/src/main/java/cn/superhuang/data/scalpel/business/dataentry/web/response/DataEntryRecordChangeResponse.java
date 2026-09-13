package cn.superhuang.data.scalpel.business.dataentry.web.response;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationType;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryRecordChange;
import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryRecordChangeStatus;

import java.time.Instant;
import java.util.UUID;

public record DataEntryRecordChangeResponse(
        UUID id,
        UUID operationLogId,
        UUID formId,
        String recordKey,
        int sequenceNo,
        DataEntryOperationType operationType,
        DataEntryRecordChangeStatus status,
        String operatorUsername,
        String businessKeySnapshot,
        String fieldSnapshot,
        String submittedSnapshot,
        String beforeSnapshot,
        String afterSnapshot,
        Instant completedAt,
        String errorCode,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {
    public static DataEntryRecordChangeResponse from(DataEntryRecordChange value) {
        return new DataEntryRecordChangeResponse(value.getId(), value.getOperationLogId(), value.getFormId(),
                value.getRecordKey(), value.getSequenceNo(), value.getOperationType(), value.getStatus(),
                value.getOperatorUsername(), value.getBusinessKeySnapshot(), value.getFieldSnapshot(),
                value.getSubmittedSnapshot(), value.getBeforeSnapshot(), value.getAfterSnapshot(),
                value.getCompletedAt(), value.getErrorCode(), value.getErrorMessage(),
                value.getCreatedAt(), value.getUpdatedAt());
    }
}

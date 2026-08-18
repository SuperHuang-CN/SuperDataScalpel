package cn.superhuang.data.scalpel.business.dataentry.web.response;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryOperationStatus;

import java.util.UUID;

public record DataEntryMutationResponse(
        UUID operationLogId,
        int requestedCount,
        int affectedCount,
        DataEntryOperationStatus status,
        boolean manualVerificationRequired,
        String warningMessage
) {

    public static DataEntryMutationResponse succeeded(UUID operationLogId, int requestedCount, int affectedCount) {
        return new DataEntryMutationResponse(
                operationLogId, requestedCount, affectedCount, DataEntryOperationStatus.SUCCEEDED, false, null
        );
    }

    public static DataEntryMutationResponse partiallySucceeded(
            UUID operationLogId,
            int requestedCount,
            int affectedCount,
            boolean manualVerificationRequired,
            String warningMessage
    ) {
        return new DataEntryMutationResponse(
                operationLogId,
                requestedCount,
                affectedCount,
                DataEntryOperationStatus.PARTIALLY_SUCCEEDED,
                manualVerificationRequired,
                warningMessage
        );
    }
}

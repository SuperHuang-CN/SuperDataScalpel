package cn.superhuang.data.scalpel.business.dataentry.service;

/**
 * Result of a target-database mutation. An incomplete result can still have changed target data because
 * data-entry mutations deliberately do not span a target-database transaction.
 */
public record DataEntryPhysicalMutationResult(
        int affectedCount,
        boolean completed,
        boolean manualVerificationRequired,
        String errorCode,
        String errorMessage
) {

    public DataEntryPhysicalMutationResult {
        if (affectedCount < 0) {
            throw new IllegalArgumentException("Affected count cannot be negative");
        }
        if (completed && (manualVerificationRequired || errorCode != null || errorMessage != null)) {
            throw new IllegalArgumentException("A completed mutation cannot carry failure details");
        }
    }

    public static DataEntryPhysicalMutationResult succeeded(int affectedCount) {
        return new DataEntryPhysicalMutationResult(affectedCount, true, false, null, null);
    }

    public static DataEntryPhysicalMutationResult incomplete(
            int affectedCount,
            boolean manualVerificationRequired,
            String errorCode,
            String errorMessage
    ) {
        return new DataEntryPhysicalMutationResult(
                affectedCount, false, manualVerificationRequired, errorCode, errorMessage
        );
    }
}

package cn.superhuang.data.scalpel.business.dataentry.web.response;

import java.util.List;
import java.util.UUID;

public record DataEntryModelCandidateResponse(
        UUID modelId,
        String modelCode,
        String modelName,
        String modelStatus,
        int schemaVersion,
        boolean knownEligible,
        List<DataEntryHealthIssueResponse> issues
) {
    public DataEntryModelCandidateResponse {
        issues = List.copyOf(issues);
    }
}

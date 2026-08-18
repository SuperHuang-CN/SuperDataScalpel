package cn.superhuang.data.scalpel.business.dataentry.web.response;

import java.util.List;

public record DataEntryHealthResponse(
        boolean canPublish,
        boolean canSubmit,
        boolean canDeleteEntries,
        boolean canQueryEntries,
        List<DataEntryHealthIssueResponse> issues
) {
    public DataEntryHealthResponse {
        issues = List.copyOf(issues);
    }
}

package cn.superhuang.data.scalpel.business.dataentry.web.response;

import java.util.List;
import java.util.UUID;

public record DataEntryHealthIssueResponse(
        String code,
        String message,
        List<String> affectedOperations,
        UUID fieldId,
        UUID sourceModelId
) {
    public DataEntryHealthIssueResponse {
        affectedOperations = affectedOperations == null ? List.of() : List.copyOf(affectedOperations);
    }
}

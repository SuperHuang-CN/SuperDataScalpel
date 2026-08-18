package cn.superhuang.data.scalpel.business.dataentry.web.response;

import cn.superhuang.data.scalpel.business.dataentry.domain.DataEntryFormStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DataEntryFormResponse(
        UUID id,
        UUID modelId,
        String modelCode,
        String modelName,
        String modelDescription,
        String modelStatus,
        Integer modelSchemaVersion,
        DataEntryFormStatus status,
        Integer publishedModelSchemaVersion,
        String healthSummary,
        List<DataEntryHealthIssueResponse> issues,
        Instant createdAt,
        Instant updatedAt
) {
    public DataEntryFormResponse {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}

package cn.superhuang.data.scalpel.business.dataentry.web.response;

import java.util.UUID;

public record DataEntryLookupResponse(
        UUID id,
        UUID targetFieldId,
        UUID sourceModelId,
        String sourceModelCode,
        String sourceModelName,
        UUID sourceValueFieldId,
        String sourceValueFieldCode,
        UUID sourceLabelFieldId,
        String sourceLabelFieldCode,
        String sourceLabelFieldName
) {
}

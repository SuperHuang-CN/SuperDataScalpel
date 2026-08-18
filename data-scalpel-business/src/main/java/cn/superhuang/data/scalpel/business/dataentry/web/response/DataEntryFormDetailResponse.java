package cn.superhuang.data.scalpel.business.dataentry.web.response;

import java.util.List;

public record DataEntryFormDetailResponse(
        DataEntryFormResponse form,
        List<DataEntryFieldResponse> fields,
        List<DataEntryLookupResponse> lookups,
        DataEntryHealthResponse health
) {
    public DataEntryFormDetailResponse {
        fields = List.copyOf(fields);
        lookups = List.copyOf(lookups);
    }
}

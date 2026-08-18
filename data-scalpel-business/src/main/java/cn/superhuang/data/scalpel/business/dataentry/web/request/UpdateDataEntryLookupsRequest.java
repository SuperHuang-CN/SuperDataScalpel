package cn.superhuang.data.scalpel.business.dataentry.web.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record UpdateDataEntryLookupsRequest(List<@Valid LookupInput> lookups) {
    public UpdateDataEntryLookupsRequest {
        lookups = lookups == null ? List.of() : List.copyOf(lookups);
    }

    public record LookupInput(
            @NotNull UUID targetFieldId,
            @NotNull UUID sourceModelId,
            @NotNull UUID sourceLabelFieldId
    ) {
    }
}

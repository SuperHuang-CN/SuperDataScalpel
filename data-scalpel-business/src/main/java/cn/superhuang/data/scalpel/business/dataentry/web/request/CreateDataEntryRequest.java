package cn.superhuang.data.scalpel.business.dataentry.web.request;

import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;

@JsonDeserialize(using = CreateDataEntryRequestDeserializer.class)
public record CreateDataEntryRequest(@NotNull Map<String, Object> values) {
    public CreateDataEntryRequest {
        values = values == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}

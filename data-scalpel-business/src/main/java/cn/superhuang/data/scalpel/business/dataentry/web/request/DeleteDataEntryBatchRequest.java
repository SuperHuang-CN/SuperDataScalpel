package cn.superhuang.data.scalpel.business.dataentry.web.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.annotation.JsonDeserialize;

import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonDeserialize(using = DeleteDataEntryBatchRequestDeserializer.class)
public record DeleteDataEntryBatchRequest(
        @NotEmpty @Size(max = 100) List<Map<String, Object>> keys
) {
    public DeleteDataEntryBatchRequest {
        keys = keys == null ? null : keys.stream()
                .map(key -> Collections.unmodifiableMap(new LinkedHashMap<>(key)))
                .toList();
    }
}

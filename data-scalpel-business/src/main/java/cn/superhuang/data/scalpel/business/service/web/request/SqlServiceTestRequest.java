package cn.superhuang.data.scalpel.business.service.web.request;

import cn.superhuang.data.scalpel.contract.service.SqlServiceParameterDefinition;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.UUID;

/** Read-only test request for an unsaved SQL service definition. */
public record SqlServiceTestRequest(
        @NotNull UUID dataSourceId,
        @NotEmpty List<@NotNull UUID> modelIds,
        @NotBlank @Size(max = 100_000) String sqlText,
        @Size(max = 50) List<@Valid SqlServiceParameterDefinition> parameters,
        Map<String, Object> arguments,
        @Min(1) @Max(50) Integer previewSize
) {

    public SqlServiceTestRequest {
        modelIds = modelIds == null ? List.of() : List.copyOf(modelIds);
        parameters = parameters == null ? List.of() : List.copyOf(parameters);
        arguments = arguments == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    }
}

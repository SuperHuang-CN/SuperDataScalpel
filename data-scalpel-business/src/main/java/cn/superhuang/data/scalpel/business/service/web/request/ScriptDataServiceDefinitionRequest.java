package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

public record ScriptDataServiceDefinitionRequest(
        @NotNull UUID dataSourceId,
        @NotBlank @Size(max = 500_000) String script,
        @NotNull @Size(min = 1, max = 50) List<@Valid ScriptRequestExampleRequest> examples
) {

    public ScriptDataServiceDefinitionRequest(UUID dataSourceId, String script) {
        this(dataSourceId, script, List.of(defaultExample()));
    }

    public static ScriptRequestExampleRequest defaultExample() {
        return new ScriptRequestExampleRequest(
                "default",
                "默认示例",
                "{\n  \n}",
                List.of(),
                List.of(new ScriptRequestParameterRequest(
                        "default-content-type", "Content-Type", "application/json"
                ))
        );
    }
}

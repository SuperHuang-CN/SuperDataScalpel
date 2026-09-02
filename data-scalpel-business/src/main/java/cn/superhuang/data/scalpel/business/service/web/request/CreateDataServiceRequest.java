package cn.superhuang.data.scalpel.business.service.web.request;

import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateDataServiceRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z][A-Za-z0-9_]{0,63}") String code,
        @NotBlank @Size(max = 100) String name,
        UUID directoryId,
        @NotNull UUID engineId,
        @Size(max = 255) String contextPath,
        @NotNull DataServiceType type,
        @Valid StandardDataServiceDefinitionRequest standardDefinition,
        @Valid SqlDataServiceDefinitionRequest sqlDefinition,
        @Valid ScriptDataServiceDefinitionRequest scriptDefinition,
        @Valid SpatialDataServiceDefinitionRequest spatialDefinition,
        @Size(max = 1000) String description
) {
    public CreateDataServiceRequest(
            String code,
            String name,
            UUID directoryId,
            UUID engineId,
            String contextPath,
            DataServiceType type,
            StandardDataServiceDefinitionRequest standardDefinition,
            SqlDataServiceDefinitionRequest sqlDefinition,
            ScriptDataServiceDefinitionRequest scriptDefinition,
            String description
    ) {
        this(
                code, name, directoryId, engineId, contextPath, type,
                standardDefinition, sqlDefinition, scriptDefinition, null, description
        );
    }
}

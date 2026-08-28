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
        @NotNull DataServiceType type,
        @Valid StandardDataServiceDefinitionRequest standardDefinition,
        @Valid SqlDataServiceDefinitionRequest sqlDefinition,
        @Valid ScriptDataServiceDefinitionRequest scriptDefinition,
        @Size(max = 1000) String description
) {

}

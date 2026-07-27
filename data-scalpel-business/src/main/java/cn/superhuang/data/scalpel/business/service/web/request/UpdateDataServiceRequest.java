package cn.superhuang.data.scalpel.business.service.web.request;

import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record UpdateDataServiceRequest(
        @NotBlank @Size(max = 100) String name,
        UUID directoryId,
        @NotNull UUID engineId,
        @NotBlank @Size(max = 255) String routePath,
        DataServiceAccessMode accessMode,
        @NotNull DataServiceType type,
        @Valid StandardDataServiceDefinitionRequest standardDefinition,
        @Valid SqlDataServiceDefinitionRequest sqlDefinition,
        @Size(max = 1000) String description
) {
}

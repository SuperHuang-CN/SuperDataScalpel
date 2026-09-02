package cn.superhuang.data.scalpel.business.service.web.request;

import jakarta.validation.Valid;

/** Replaces the complete design-time definition for the service's immutable type. */
public record UpdateDataServiceDefinitionRequest(
        @Valid StandardDataServiceDefinitionRequest standardDefinition,
        @Valid SqlDataServiceDefinitionRequest sqlDefinition,
        @Valid ScriptDataServiceDefinitionRequest scriptDefinition,
        @Valid SpatialDataServiceDefinitionRequest spatialDefinition
) {
    public UpdateDataServiceDefinitionRequest(
            StandardDataServiceDefinitionRequest standardDefinition,
            SqlDataServiceDefinitionRequest sqlDefinition,
            ScriptDataServiceDefinitionRequest scriptDefinition
    ) {
        this(standardDefinition, sqlDefinition, scriptDefinition, null);
    }
}

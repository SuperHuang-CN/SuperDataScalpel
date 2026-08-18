package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceAccessMode;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record UpdateSparkJarTaskDefinitionRequest(
        @NotNull @Size(max = 100) List<@NotNull @Valid Entry> parameters,
        @NotNull @Size(max = 100) List<@NotNull @Valid Entry> sparkConf,
        @NotNull @Size(max = 200) List<@NotNull @Valid ResourceBinding> resourceBindings,
        @Min(1) @Max(86400) int timeoutSeconds
) {
    public record Entry(@NotBlank @Size(max = 500) String name, @NotNull @Size(max = 4000) String value) {}

    public record ResourceBinding(
            @NotBlank @Size(max = 100) String bindingName,
            @NotNull SparkJarResourceType resourceType,
            @NotNull UUID resourceId,
            @Size(max = 249) String topicName,
            @NotNull SparkJarResourceAccessMode accessMode
    ) {
        public ResourceBinding(String bindingName, SparkJarResourceType resourceType, UUID resourceId,
                               SparkJarResourceAccessMode accessMode) {
            this(bindingName, resourceType, resourceId, null, accessMode);
        }
    }
}

package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceAccessMode;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourceSpec;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

public record UpdateSparkJarTaskDefinitionRequest(
        @NotNull @Size(max = 100) List<@NotNull @Valid Entry> parameters,
        @NotNull @Size(max = 100) List<@NotNull @Valid Entry> sparkConf,
        @NotNull @Size(max = 200) List<@NotNull @Valid ResourceBinding> resourceBindings,
        @Valid ExecutionResources executionResources,
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

    public record ExecutionResources(
            @NotNull(message = "驱动 CPU 不能为空")
            @Min(value = SparkExecutionResourceSpec.MIN_CORES, message = "驱动 CPU 不能小于 1")
            @Max(value = SparkExecutionResourceSpec.MAX_CORES, message = "驱动 CPU 不能超过 256")
            Integer driverCores,
            @NotNull(message = "驱动内存不能为空")
            @Min(value = SparkExecutionResourceSpec.MIN_MEMORY_MIB, message = "驱动内存不能小于 1024 MiB")
            @Max(value = SparkExecutionResourceSpec.MAX_MEMORY_MIB, message = "驱动内存超过平台允许范围")
            Integer driverMemoryMiB,
            @NotNull(message = "执行器数量不能为空")
            @Min(value = SparkExecutionResourceSpec.MIN_EXECUTORS, message = "执行器数量不能小于 1")
            @Max(value = SparkExecutionResourceSpec.MAX_EXECUTORS, message = "执行器数量不能超过 10000")
            Integer executorInstances,
            @NotNull(message = "单执行器 CPU 不能为空")
            @Min(value = SparkExecutionResourceSpec.MIN_CORES, message = "单执行器 CPU 不能小于 1")
            @Max(value = SparkExecutionResourceSpec.MAX_CORES, message = "单执行器 CPU 不能超过 256")
            Integer executorCores,
            @NotNull(message = "单执行器内存不能为空")
            @Min(value = SparkExecutionResourceSpec.MIN_MEMORY_MIB, message = "单执行器内存不能小于 1024 MiB")
            @Max(value = SparkExecutionResourceSpec.MAX_MEMORY_MIB, message = "单执行器内存超过平台允许范围")
            Integer executorMemoryMiB
    ) {
        public SparkExecutionResourceSpec toSpec() {
            return new SparkExecutionResourceSpec(
                    driverCores, driverMemoryMiB, executorInstances, executorCores, executorMemoryMiB);
        }
    }
}

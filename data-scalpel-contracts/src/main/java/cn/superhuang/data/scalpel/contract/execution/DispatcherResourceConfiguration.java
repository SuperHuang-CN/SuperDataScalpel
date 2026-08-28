package cn.superhuang.data.scalpel.contract.execution;

/**
 * Safe, effective resource configuration for one execution. Fields unrelated to the backend type are null.
 * This describes configured limits, not live resource usage.
 */
public record DispatcherResourceConfiguration(
        ExecutionBackendType backendType,
        String image,
        String containerCpuLimit,
        String containerMemoryLimit,
        String runnerJvmHeap,
        String queue,
        String namespace,
        String driverMemory,
        String executorMemory,
        Integer executorCores,
        Integer executorInstances
) {
}

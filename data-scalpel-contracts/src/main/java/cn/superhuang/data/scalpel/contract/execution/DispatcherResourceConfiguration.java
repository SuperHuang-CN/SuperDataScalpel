package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/**
 * Safe, effective resource configuration for one execution. Fields unrelated to the backend type are null.
 * This describes configured limits, not live resource usage.
 */
@JsonClassDescription("Dispatcher 当前目标的部署资源默认值及安全后端信息；来自 resource-policy.defaults，不是任务自定义值、资源上限或实时用量，与后端无关的字段为空。")
public record DispatcherResourceConfiguration(
        @JsonPropertyDescription("当前 Dispatcher 固定执行后端：LOCAL_DOCKER、YARN 或 KUBERNETES。")
        ExecutionBackendType backendType,
        @JsonPropertyDescription("Runner 容器镜像；LOCAL_DOCKER 和 KUBERNETES 使用，YARN 后端为空。")
        String image,
        @JsonPropertyDescription("本地 Docker Runner 容器的 CPU 限额字符串；仅 LOCAL_DOCKER 使用。")
        String containerCpuLimit,
        @JsonPropertyDescription("本地 Docker Runner 容器的内存限额字符串；仅 LOCAL_DOCKER 使用。")
        String containerMemoryLimit,
        @JsonPropertyDescription("LOCAL_DOCKER 默认容器内存的 75% 所对应的 JVM 最大堆，如 -Xmx3072m；其他后端为空。")
        String runnerJvmHeap,
        @JsonPropertyDescription("Spark 应用提交到的 YARN 队列；仅 YARN 后端返回。")
        String queue,
        @JsonPropertyDescription("SparkApplication 所在 Kubernetes Namespace；仅 KUBERNETES 后端返回。")
        String namespace,
        @JsonPropertyDescription("目标策略中的默认 Spark Driver JVM 堆内存；YARN/KUBERNETES 返回，容器还需额外非堆内存。")
        String driverMemory,
        @JsonPropertyDescription("目标策略中的默认单个 Spark Executor JVM 堆内存；YARN/KUBERNETES 返回，容器还需额外非堆内存。")
        String executorMemory,
        @JsonPropertyDescription("Dispatcher 部署配置的单个 Spark Executor CPU 核数；YARN 或 KUBERNETES 后端返回。")
        Integer executorCores,
        @JsonPropertyDescription("Dispatcher 部署配置的 Spark Executor 实例数量；YARN 或 KUBERNETES 后端返回。")
        Integer executorInstances
) {
}

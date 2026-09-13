package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/**
 * Safe, effective resource configuration for one execution. Fields unrelated to the backend type are null.
 * This describes configured limits, not live resource usage.
 */
@JsonClassDescription("Dispatcher 对当前执行后端生效的安全资源配置；与后端无关的字段为空，不表示实时资源用量。")
public record DispatcherResourceConfiguration(
        @JsonPropertyDescription("当前 Dispatcher 固定执行后端：LOCAL_DOCKER、YARN 或 KUBERNETES。")
        ExecutionBackendType backendType,
        @JsonPropertyDescription("Runner 容器镜像；LOCAL_DOCKER 和 KUBERNETES 使用，YARN 后端为空。")
        String image,
        @JsonPropertyDescription("本地 Docker Runner 容器的 CPU 限额字符串；仅 LOCAL_DOCKER 使用。")
        String containerCpuLimit,
        @JsonPropertyDescription("本地 Docker Runner 容器的内存限额字符串；仅 LOCAL_DOCKER 使用。")
        String containerMemoryLimit,
        @JsonPropertyDescription("从 Runner Java 参数解析出的 JVM 最大堆设置，例如 -Xmx3g；仅配置了该参数时返回。")
        String runnerJvmHeap,
        @JsonPropertyDescription("Spark 应用提交到的 YARN 队列；仅 YARN 后端返回。")
        String queue,
        @JsonPropertyDescription("SparkApplication 所在 Kubernetes Namespace；仅 KUBERNETES 后端返回。")
        String namespace,
        @JsonPropertyDescription("Dispatcher 部署配置的 Spark Driver 内存；YARN 或 KUBERNETES 后端返回。")
        String driverMemory,
        @JsonPropertyDescription("Dispatcher 部署配置的单个 Spark Executor 内存；YARN 或 KUBERNETES 后端返回。")
        String executorMemory,
        @JsonPropertyDescription("Dispatcher 部署配置的单个 Spark Executor CPU 核数；YARN 或 KUBERNETES 后端返回。")
        Integer executorCores,
        @JsonPropertyDescription("Dispatcher 部署配置的 Spark Executor 实例数量；YARN 或 KUBERNETES 后端返回。")
        Integer executorInstances
) {
}

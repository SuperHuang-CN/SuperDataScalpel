package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/**
 * Requested Spark driver and executor resources for one execution. Values are
 * deliberately expressed without Spark runtime types so the same contract can
 * travel from Admin to Dispatcher unchanged.
 */
@JsonClassDescription("单次 Spark 执行申请的 Driver 与 Executor 资源规格；跨 Admin、Dispatcher 和 Runner 传递，不表示实时资源用量。")
public record SparkExecutionResourceSpec(
        @JsonPropertyDescription("Spark Driver 申请的 CPU 核数，范围 1 到 256；LOCAL_DOCKER 映射为容器 CPU 上限。")
        int driverCores,
        @JsonPropertyDescription("Spark Driver 申请的内存，单位 MiB，范围 1024 到 1048576；LOCAL_DOCKER 映射为容器内存上限。")
        int driverMemoryMiB,
        @JsonPropertyDescription("Executor 实例数量，范围 1 到 10000；YARN 和 KUBERNETES 使用，LOCAL_DOCKER 的 local 模式忽略。")
        int executorInstances,
        @JsonPropertyDescription("每个 Executor 申请的 CPU 核数，范围 1 到 256；YARN 和 KUBERNETES 使用，LOCAL_DOCKER 忽略。")
        int executorCores,
        @JsonPropertyDescription("每个 Executor 申请的内存，单位 MiB，范围 1024 到 1048576；YARN 和 KUBERNETES 使用，LOCAL_DOCKER 忽略。")
        int executorMemoryMiB
) {
    public static final int MIN_CORES = 1;
    public static final int MAX_CORES = 256;
    public static final int MIN_MEMORY_MIB = 1024;
    public static final int MAX_MEMORY_MIB = 1_048_576;
    public static final int MIN_EXECUTORS = 1;
    public static final int MAX_EXECUTORS = 10_000;

    public SparkExecutionResourceSpec {
        if (driverCores < MIN_CORES || driverCores > MAX_CORES
                || executorCores < MIN_CORES || executorCores > MAX_CORES
                || driverMemoryMiB < MIN_MEMORY_MIB || driverMemoryMiB > MAX_MEMORY_MIB
                || executorMemoryMiB < MIN_MEMORY_MIB || executorMemoryMiB > MAX_MEMORY_MIB
                || executorInstances < MIN_EXECUTORS || executorInstances > MAX_EXECUTORS) {
            throw new IllegalArgumentException("Spark 运行资源超出允许范围");
        }
    }

    public boolean exceeds(SparkExecutionResourceSpec maximums) {
        if (maximums == null) throw new IllegalArgumentException("资源上限不能为空");
        return driverCores > maximums.driverCores
                || driverMemoryMiB > maximums.driverMemoryMiB
                || executorInstances > maximums.executorInstances
                || executorCores > maximums.executorCores
                || executorMemoryMiB > maximums.executorMemoryMiB;
    }
}

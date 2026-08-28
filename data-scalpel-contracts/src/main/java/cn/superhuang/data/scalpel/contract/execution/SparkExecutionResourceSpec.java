package cn.superhuang.data.scalpel.contract.execution;

/**
 * Requested Spark driver and executor resources for one execution. Values are
 * deliberately expressed without Spark runtime types so the same contract can
 * travel from Admin to Dispatcher unchanged.
 */
public record SparkExecutionResourceSpec(
        int driverCores,
        int driverMemoryMiB,
        int executorInstances,
        int executorCores,
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

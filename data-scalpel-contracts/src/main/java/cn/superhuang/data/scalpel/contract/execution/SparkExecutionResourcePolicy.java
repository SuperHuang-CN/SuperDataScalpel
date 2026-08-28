package cn.superhuang.data.scalpel.contract.execution;

/** Default and single-execution maximum resources enforced by a compute engine. */
public record SparkExecutionResourcePolicy(
        SparkExecutionResourceSpec defaults,
        SparkExecutionResourceSpec maximums
) {
    public SparkExecutionResourcePolicy {
        if (defaults == null || maximums == null || defaults.exceeds(maximums)) {
            throw new IllegalArgumentException("Spark 运行资源默认值不能超过上限");
        }
    }

    public static SparkExecutionResourcePolicy defaultsFor(ExecutionBackendType backendType) {
        if (backendType == null) throw new IllegalArgumentException("执行后端类型不能为空");
        return switch (backendType) {
            case LOCAL_DOCKER -> new SparkExecutionResourcePolicy(
                    new SparkExecutionResourceSpec(2, 4096, 1, 1, 1024),
                    new SparkExecutionResourceSpec(8, 16384, 1, 1, 1024));
            case YARN, KUBERNETES -> new SparkExecutionResourcePolicy(
                    new SparkExecutionResourceSpec(1, 2048, 2, 2, 2048),
                    new SparkExecutionResourceSpec(8, 16384, 20, 8, 16384));
        };
    }
}

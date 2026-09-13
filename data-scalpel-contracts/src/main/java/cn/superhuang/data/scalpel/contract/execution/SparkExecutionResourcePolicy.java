package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Default and single-execution maximum resources enforced by a compute engine. */
@JsonClassDescription("计算引擎对单次 Spark 执行采用的默认资源和允许申请的最大资源策略。")
public record SparkExecutionResourcePolicy(
        @JsonPropertyDescription("单次 Spark 执行未单独声明资源时采用的默认值；每一项都必须小于或等于 maximums。")
        SparkExecutionResourceSpec defaults,
        @JsonPropertyDescription("单次 Spark 执行允许申请的最大资源；这是单任务上限，不是集群总容量或并发任务配额。")
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

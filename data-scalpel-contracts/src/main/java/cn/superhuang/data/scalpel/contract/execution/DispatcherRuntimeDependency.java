package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
/** Safe readiness result for one Dispatcher dependency. */
@JsonClassDescription("Dispatcher 的一个运行依赖就绪结果；仅包含依赖名称、可用状态和脱敏说明。")
public record DispatcherRuntimeDependency(
        @JsonPropertyDescription("依赖的稳定名称，例如 backend、artifact-storage、kafka 或 kafka-listeners。")
        String name,
        @JsonPropertyDescription("就绪状态：UP 表示可用，DOWN 表示存在阻塞问题。")
        String state,
        @JsonPropertyDescription("已脱敏的问题摘要；依赖正常或没有补充信息时为空。")
        String detail
) {
}

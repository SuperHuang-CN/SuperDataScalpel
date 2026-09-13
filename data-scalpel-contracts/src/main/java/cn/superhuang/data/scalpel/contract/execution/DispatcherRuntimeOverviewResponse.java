package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Current read-only operational snapshot of one Dispatcher instance. */
public record DispatcherRuntimeOverviewResponse(
        @JsonPropertyDescription("当前 Dispatcher 已绑定的 Admin 计算引擎 UUID；尚未注册时为空。")
        UUID engineId,
        @JsonPropertyDescription("Dispatcher 安装首次启动时生成并持久化的稳定实例标识。")
        String dispatcherInstanceId,
        @JsonPropertyDescription("当前 Dispatcher 固定执行后端：LOCAL_DOCKER、YARN 或 KUBERNETES。")
        ExecutionBackendType backendType,
        @JsonPropertyDescription("Dispatcher 应用版本。")
        String version,
        @JsonPropertyDescription("Dispatcher 绑定状态：UNREGISTERED、INACTIVE、ACTIVE、DRAINING 或 ERROR。")
        String registrationState,
        @JsonPropertyDescription("执行后端、制品存储、Kafka 和监听器等依赖的即时就绪结果；没有条目时为空列表。")
        List<DispatcherRuntimeDependency> dependencies,
        @JsonPropertyDescription("当前注册实际采用的准入上限；尚未注册时为空。")
        DispatcherAdmissionCapacity admissionCapacity,
        @JsonPropertyDescription("按 Dispatcher 持久化执行账本统计的当前状态数量。")
        DispatcherAdmissionUsage admissionUsage,
        @JsonPropertyDescription("Dispatcher 部署当前生效的后端资源配置；描述配置值，不表示实时利用率。")
        DispatcherResourceConfiguration resourceConfiguration,
        @JsonPropertyDescription("Dispatcher 生成该运行态快照的时间，ISO-8601 UTC 时间戳。")
        Instant collectedAt
) {
    public DispatcherRuntimeOverviewResponse {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}

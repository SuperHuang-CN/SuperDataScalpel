package cn.superhuang.data.scalpel.business.compute.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.contract.execution.DispatcherAdmissionCapacity;
import cn.superhuang.data.scalpel.contract.execution.DispatcherAdmissionUsage;
import cn.superhuang.data.scalpel.contract.execution.DispatcherResourceConfiguration;
import cn.superhuang.data.scalpel.contract.execution.DispatcherRuntimeDependency;
import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Safe operational snapshot of the Dispatcher currently configured for one compute engine. */
@Schema(description = "从当前 Dispatcher 实时读取的只读运行态快照；Admin 不持久化该快照，控制面不可达或身份不一致时接口返回 502。")
public record ComputeEngineRuntimeOverviewResponse(
        @Schema(description = "本次查询的 Admin 计算引擎 UUID。")
        UUID engineId,
        @Schema(description = "Dispatcher 实时上报的稳定实例标识；Admin 已保存 dispatcherInstanceId 时必须一致，尚未锁定身份时只要求非空。该查询不会把身份写回 Admin。")
        String dispatcherInstanceId,
        @Schema(description = "Dispatcher 实际执行后端：LOCAL_DOCKER、YARN 或 KUBERNETES；必须与计算引擎期望值一致。")
        ExecutionBackendType backendType,
        @Schema(description = "Dispatcher 应用版本。")
        String version,
        @Schema(description = "Dispatcher 自身的绑定状态：UNREGISTERED、INACTIVE、ACTIVE、DRAINING 或 ERROR。")
        String dispatcherRegistrationState,
        @Schema(description = "Dispatcher 对执行后端、制品存储、Kafka 和监听器等依赖的就绪检查；没有条目时为空列表。")
        List<DispatcherRuntimeDependency> dependencies,
        @Schema(description = "当前注册实际采用的队列、并发提交和在途应用上限；Dispatcher 尚未注册时为空。")
        DispatcherAdmissionCapacity admissionCapacity,
        @Schema(description = "按 Dispatcher 持久化执行账本统计的当前各状态数量；不是 Kafka Consumer Lag。")
        DispatcherAdmissionUsage admissionUsage,
        @Schema(description = "Dispatcher 部署当前生效的后端资源配置；与后端无关的字段为空，表示配置值而非实时资源使用率。")
        DispatcherResourceConfiguration resourceConfiguration,
        @Schema(description = "Dispatcher 生成该快照的时间，ISO-8601 UTC 时间戳。")
        Instant collectedAt
) {
    public ComputeEngineRuntimeOverviewResponse {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}

package cn.superhuang.data.scalpel.business.compute.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngine;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineHealthState;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeEngineRegistrationState;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "Admin 保存的计算引擎配置及最近一次控制面检查状态；不包含 Dispatcher Token 明文、实时负载或历史指标。")
public record ComputeEngineResponse(
        @Schema(description = "Admin 中计算引擎的稳定 UUID；任务通过该值选择执行位置。")
        UUID id,
        @Schema(description = "计算引擎展示名称；忽略大小写全局唯一。")
        String name,
        @Schema(description = "计算引擎用途或部署位置说明；未填写时为空。")
        String description,
        @Schema(description = "Admin 访问 Task Dispatcher 控制面的 HTTP/HTTPS 根地址。")
        String dispatcherBaseUrl,
        @Schema(description = "是否已保存 Dispatcher 访问令牌；仅表示配置存在，不表示令牌当前有效。")
        boolean accessTokenConfigured,
        @Schema(description = "管理员配置的期望执行后端；注册和运行前必须与 Dispatcher 实际上报类型一致。")
        ComputeBackendType expectedBackendType,
        @Schema(description = "最近一次成功测试或注册时 Dispatcher 上报的执行后端；尚未成功检查、任何保存配置发生变化或离线解绑后为空。")
        ComputeBackendType reportedBackendType,
        @Schema(description = "Admin 记录的注册生命周期状态；它与最近一次健康检查结果相互独立。")
        ComputeEngineRegistrationState registrationState,
        @Schema(description = "最近一次主动控制面检查结果：UNKNOWN 表示尚未检查或配置刚变化，UP 表示成功，DOWN 表示失败或已离线解绑。")
        ComputeEngineHealthState healthState,
        @Schema(description = "Admin 向 Dispatcher 发布提交、取消等命令的 Kafka Topic；在全部计算引擎中唯一。")
        String commandTopic,
        @Schema(description = "Runner 向 Dispatcher 发布运行事件的 Kafka 主题。")
        String runnerEventTopic,
        @Schema(description = "Dispatcher 向 Admin 发布执行状态事件的 Kafka Topic；可由多个计算引擎共享。")
        String adminEventTopic,
        @Schema(description = "允许等待提交的执行数量上限；0 表示不允许排队。")
        int maxQueuedExecutions,
        @Schema(description = "同时执行外部提交命令的数量上限。")
        int maxConcurrentSubmissions,
        @Schema(description = "已提交但尚未终止的外部应用数量上限；0 表示不设置额外上限。")
        int maxInFlightApplications,
        @Schema(description = "Spark 单次执行的默认资源和申请上限；这些值不表示集群总容量或实时利用率。")
        SparkExecutionResourcePolicy resourcePolicy,
        @Schema(description = "最近一次成功测试或注册后锁定的 Dispatcher 稳定实例标识；未检查、任何保存配置发生变化或离线解绑后为空，后续身份不一致会阻止管理和执行。")
        String dispatcherInstanceId,
        @Schema(description = "最近一次测试、注册、Drain、反注册、离线解绑或其失败写入本地状态的时间；尚未执行这些动作或配置变化后为空。")
        Instant lastCheckAt,
        @Schema(description = "最近一次安全错误摘要；没有错误时为空。")
        String lastError,
        @Schema(description = "最近一次离线解除绑定时间；未处于 DETACHED 状态时为空。")
        Instant detachedAt,
        @Schema(description = "管理员填写的离线解除绑定原因；未处于 DETACHED 状态时为空。")
        String detachReason,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static ComputeEngineResponse from(ComputeEngine engine, SparkExecutionResourcePolicy resourcePolicy) {
        return new ComputeEngineResponse(
                engine.getId(), engine.getName(), engine.getDescription(), engine.getDispatcherBaseUrl(),
                true, engine.getExpectedBackendType(), engine.getReportedBackendType(),
                engine.getRegistrationState(), engine.getHealthState(), engine.getCommandTopic(),
                engine.getRunnerEventTopic(), engine.getAdminEventTopic(), engine.getMaxQueuedExecutions(),
                engine.getMaxConcurrentSubmissions(), engine.getMaxInFlightApplications(),
                resourcePolicy,
                engine.getDispatcherInstanceId(), engine.getLastCheckAt(), engine.getLastError(),
                engine.getDetachedAt(), engine.getDetachReason(),
                engine.getCreatedAt(), engine.getUpdatedAt()
        );
    }
}

package cn.superhuang.data.scalpel.business.compute.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "完整更新计算引擎配置。CREATED、INACTIVE、ERROR、DETACHED 使用普通更新；ACTIVE、DRAINING 必须使用重新配置动作。")
public record UpdateComputeEngineRequest(
        @Schema(description = "计算引擎展示名称；去除首尾空白后保存，忽略大小写全局唯一。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "计算引擎用途或部署位置说明；空白值按未填写处理。")
        @Size(max = 1000) String description,
        @Schema(description = "Admin 访问 Task Dispatcher 控制面的 HTTP/HTTPS 根地址；去除首尾空白及全部末尾斜杠后保存。")
        @NotBlank @Size(max = 500) String dispatcherBaseUrl,
        @Schema(description = "新的 Dispatcher Bearer Token；缺失、null 或空白时保留当前 Token，非空时替换并加密保存，响应不返回该值。", accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @Size(max = 1000) String accessToken,
        @Schema(description = "期望执行后端：LOCAL_DOCKER、YARN 或 KUBERNETES；测试和注册时与 Dispatcher 上报值核对。")
        @NotNull ComputeBackendType expectedBackendType,
        @Schema(description = "Admin 向 Dispatcher 发布提交、取消等命令的 Kafka Topic；格式受限，并在全部计算引擎中唯一。")
        @NotBlank @Size(max = 249) String commandTopic,
        @Schema(description = "Runner 向 Dispatcher 发布状态和结果事件的 Kafka Topic；格式受限，并在全部计算引擎中唯一。Runner 控制 Topic 固定派生为该值加“.control”。")
        @NotBlank @Size(max = 249) String runnerEventTopic,
        @Schema(description = "Dispatcher 向 Admin 发布执行状态事件的 Kafka Topic；必须已包含在当前 Admin 的监听配置中，可由多个计算引擎共享。")
        @NotBlank @Size(max = 249) String adminEventTopic,
        @Schema(description = "Dispatcher 允许等待提交的执行数量上限；达到上限时拒绝新执行，0 表示不允许排队。", minimum = "0")
        @Min(0) int maxQueuedExecutions,
        @Schema(description = "Dispatcher 同时执行外部提交命令的数量上限。", minimum = "1")
        @Min(1) int maxConcurrentSubmissions,
        @Schema(description = "已提交但尚未终止的外部应用数量上限；0 表示不设置额外上限。", minimum = "0")
        @Min(0) int maxInFlightApplications,
        @Schema(description = "新的 Spark 单次执行资源策略；为空时保留当前策略，即使 expectedBackendType 同时改变也不会换成新后端默认值。降低资源上限时，若已有绑定的 Spark JAR 或实时 JAR 任务申请超限，则整次更新返回冲突且不自动修改任务。")
        @Valid SparkExecutionResourcePolicy resourcePolicy
) {
}

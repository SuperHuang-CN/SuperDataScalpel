package cn.superhuang.data.scalpel.business.compute.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;
import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "创建一条计算引擎配置。创建只保存配置，不会连接或注册 Dispatcher；新记录初始为 CREATED/UNKNOWN。")
public record CreateComputeEngineRequest(
        @Schema(description = "计算引擎展示名称；去除首尾空白后保存，忽略大小写全局唯一。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "计算引擎用途或部署位置说明；空白值按未填写处理。")
        @Size(max = 1000) String description,
        @Schema(description = "Admin 访问 Task Dispatcher 控制面的 HTTP/HTTPS 根地址；去除首尾空白及全部末尾斜杠后保存，不从浏览器地址或代理头推导。")
        @NotBlank @Size(max = 500) String dispatcherBaseUrl,
        @Schema(description = "Admin 调用 Dispatcher 控制面的 Bearer Token；仅本次写入，服务端加密保存且任何响应都不返回明文或密文。", accessMode = Schema.AccessMode.WRITE_ONLY, format = "password")
        @NotBlank @Size(max = 1000) String accessToken,
        @Schema(description = "期望 Dispatcher 固定使用的执行后端：LOCAL_DOCKER 为本机 Docker，YARN 为 YARN cluster，KUBERNETES 为 Kubernetes cluster；测试和注册时会与 Dispatcher 上报值核对。")
        @NotNull ComputeBackendType expectedBackendType,
        @Schema(description = "Admin 向 Dispatcher 发布提交、取消等命令的 Kafka Topic；只允许字母、数字、点、下划线和连字符，并在全部计算引擎中唯一。")
        @NotBlank @Size(max = 249) String commandTopic,
        @Schema(description = "Runner 向 Dispatcher 发布状态和结果事件的 Kafka Topic；格式同 commandTopic，并在全部计算引擎中唯一。Runner 控制 Topic 固定派生为该值加“.control”。")
        @NotBlank @Size(max = 249) String runnerEventTopic,
        @Schema(description = "Dispatcher 向 Admin 发布执行状态事件的 Kafka Topic；必须已包含在当前 Admin 的监听配置中，可由多个计算引擎共享。")
        @NotBlank @Size(max = 249) String adminEventTopic,
        @Schema(description = "Dispatcher 允许等待提交的执行数量上限；达到上限时拒绝新执行，0 表示不允许排队。", minimum = "0")
        @Min(0) int maxQueuedExecutions,
        @Schema(description = "Dispatcher 同时执行外部提交命令的数量上限。", minimum = "1")
        @Min(1) int maxConcurrentSubmissions,
        @Schema(description = "已提交但尚未终止的外部应用数量上限；0 表示不设置额外上限。", minimum = "0")
        @Min(0) int maxInFlightApplications,
        @Schema(description = "Spark 单次执行资源策略，包含默认申请值和允许上限；为空时按 expectedBackendType 生成系统默认策略。LOCAL_DOCKER 默认 Driver 2 核/4096 MiB、上限 8 核/16384 MiB；YARN/KUBERNETES 默认 Driver 1 核/2048 MiB、2 个 Executor 各 2 核/2048 MiB，上限为 Driver 8 核/16384 MiB、20 个 Executor 各 8 核/16384 MiB。")
        @Valid SparkExecutionResourcePolicy resourcePolicy
) {
}

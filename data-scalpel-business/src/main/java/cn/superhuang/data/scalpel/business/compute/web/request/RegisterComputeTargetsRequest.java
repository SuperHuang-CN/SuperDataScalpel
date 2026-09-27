package cn.superhuang.data.scalpel.business.compute.web.request;

import cn.superhuang.data.scalpel.contract.execution.SparkExecutionResourcePolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

@Schema(description = "批量注册已发现目标；每项目标独立保存和注册，部分失败不回滚成功项")
public record RegisterComputeTargetsRequest(
        @Schema(description = "Dispatcher HTTP/HTTPS 地址") @NotBlank @Size(max = 500) String dispatcherBaseUrl,
        @Schema(description = "访问 Token；创建新引擎时加密保存，已存在引擎不会被覆盖") @NotBlank @Size(max = 2000) String accessToken,
        @Schema(description = "发现返回的 Dispatcher 持久化实例身份，必须与当前实例一致") @NotBlank @Size(max = 100) String dispatcherInstanceId,
        @Schema(description = "要注册的不同目标，1 到 32 项；已存在记录重用其配置，不重复创建") @NotEmpty @Size(min = 1, max = 32) List<@Valid Target> targets) {
    @Schema(description = "待注册目标及初始运行策略")
    public record Target(
            @Schema(description = "发现返回的目标键") @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,62}") String targetKey,
            @Schema(description = "发现返回的物理目标身份摘要") @NotBlank @Pattern(regexp = "[a-f0-9]{64}") String targetFingerprint,
            @Schema(description = "新引擎显示名称，平台内唯一；已有记录不改名") @NotBlank @Size(max = 100) String name,
            @Schema(description = "最大排队数，0 表示不允许排队") @Min(0) @Max(100000) int maxQueuedExecutions,
            @Schema(description = "并发提交数，1 到 64") @Min(1) @Max(64) int maxConcurrentSubmissions,
            @Schema(description = "最大在途应用数，0 表示不限制") @Min(0) @Max(100000) int maxInFlightApplications,
            @Schema(description = "旧客户端兼容字段，不再用于配置。Admin 始终从 Dispatcher 目标目录读取默认值和上限，忽略客户端传入值") @Valid SparkExecutionResourcePolicy resourcePolicy) { }
}

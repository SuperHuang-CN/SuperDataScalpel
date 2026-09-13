package cn.superhuang.data.scalpel.business.compute.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherCapabilities;
import cn.superhuang.data.scalpel.business.compute.client.DispatcherDependency;
import cn.superhuang.data.scalpel.business.compute.domain.ComputeBackendType;

import java.util.List;

@Schema(description = "对已保存 Dispatcher 地址和 Token 执行实时 /info 检查后的结果；只要控制面可访问且实例身份、后端类型通过核对即成功并把健康状态更新为 UP。dependencies 中存在 DOWN 不会使本测试失败；该接口也不会注册 Dispatcher或提交测试任务。")
public record ComputeEngineTestResponse(
        @Schema(description = "Dispatcher 上报的稳定实例标识；与已锁定身份不一致时测试失败。")
        String dispatcherInstanceId,
        @Schema(description = "Dispatcher 上报的固定执行后端；与计算引擎 expectedBackendType 不一致时测试失败。")
        ComputeBackendType backendType,
        @Schema(description = "Dispatcher 版本。")
        String dispatcherVersion,
        @Schema(description = "Dispatcher 声明支持的取消、日志、重启恢复和实时任务能力。")
        DispatcherCapabilities capabilities,
        @Schema(description = "Dispatcher 对执行后端、制品存储和 Kafka 等依赖的诊断结果；可能包含 DOWN，测试接口不会据此失败。没有条目时为空列表。")
        List<DispatcherDependency> dependencies
) {
    public ComputeEngineTestResponse {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}

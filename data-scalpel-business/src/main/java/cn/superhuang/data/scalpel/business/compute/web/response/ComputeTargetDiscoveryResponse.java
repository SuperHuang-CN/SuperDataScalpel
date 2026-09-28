package cn.superhuang.data.scalpel.business.compute.web.response;

import cn.superhuang.data.scalpel.contract.execution.DispatcherTargetDirectoryResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Dispatcher 目标与本平台注册情况；只读，不变更任何配置")
public record ComputeTargetDiscoveryResponse(
        @Schema(description = "持久化 Dispatcher 实例身份") String dispatcherInstanceId,
        @Schema(description = "控制面协议版本，3 为实例共享消息通道，2 为旧版") int controlPlaneVersion,
        @Schema(description = "实例实际生效的共享消息通道；旧版为 null，只读不可编辑") DispatcherTargetDirectoryResponse.Messaging messaging,
        @Schema(description = "可发现目标及本平台对应记录") List<Target> targets) {
    @Schema(description = "目标发现结果与现有引擎记录")
    public record Target(
            @Schema(description = "Dispatcher 返回的安全目标元数据") DispatcherTargetDirectoryResponse.Target target,
            @Schema(description = "本平台现有引擎；尚未注册时为空，失败项重试使用该记录") ComputeEngineResponse engine) { }
}

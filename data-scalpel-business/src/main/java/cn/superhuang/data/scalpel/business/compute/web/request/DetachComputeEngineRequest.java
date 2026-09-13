package cn.superhuang.data.scalpel.business.compute.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "在 Dispatcher 控制面因传输故障不可达时，仅在 Admin 本地解除绑定的灾难恢复请求；该操作不会停止远端进程或远端任务。")
public record DetachComputeEngineRequest(
        @Schema(description = "防误操作确认值；去除首尾空白后必须与当前计算引擎名称完全一致。")
        @NotBlank @Size(max = 100) String confirmationName,
        @Schema(description = "管理员记录的离线解除绑定原因，例如原 Dispatcher 主机已永久下线；去除首尾空白后保存。")
        @NotBlank @Size(max = 500) String reason
) {
}

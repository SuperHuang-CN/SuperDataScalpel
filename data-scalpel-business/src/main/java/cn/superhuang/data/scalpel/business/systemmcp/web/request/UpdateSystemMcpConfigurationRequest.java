package cn.superhuang.data.scalpel.business.systemmcp.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "原子保存系统 MCP 总开关和指定接口的开放状态变更")
public record UpdateSystemMcpConfigurationRequest(
        @Schema(description = "系统 MCP 总开关；为空表示保持当前值") Boolean enabled,
        @Schema(description = "本次需要修改的接口项，最多 2000 项；为空或空列表表示不修改接口开放状态，未列出的接口保持原状态。存在重复、缺失或不可开放的目标时整批不生效")
        @Size(max = 2000) List<@NotNull @Valid ApiChange> changes
) {
    @Schema(description = "一个接口开放状态变更")
    public record ApiChange(
            @Schema(description = "接口目录记录 UUID；不是 operationId 或业务资源 UUID") @NotNull UUID id,
            @Schema(description = "true 开放接口，false 关闭接口") @NotNull Boolean enabled
    ) {
    }
}

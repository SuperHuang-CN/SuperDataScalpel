package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.contract.task.*;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "整体保存 DRAFT 或 DISABLED Canvas 任务定义；服务端先升级兼容协议，再校验并以当前协议持久化。内容未变化时定义版本不递增；Canvas 协议版本与任务定义版本分别管理。")
public record UpdateCanvasTaskDefinitionRequest(
        @Schema(description = "完整 Canvas 定义，序列化后的 UTF-8 内容不得超过 5 MiB。服务端校验协议兼容、节点图、配置及任务固定执行模式；流式任务有活动部署时不能保存。") @NotNull CanvasDefinition definition
) {
}

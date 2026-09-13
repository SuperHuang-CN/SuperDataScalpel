package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.business.task.domain.WorkflowDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "完整保存 DRAFT 或 DISABLED 工作流的节点、依赖和布局；PUBLISHED 工作流必须先停用。内容实际变化时定义版本递增，重复保存相同规范化 JSON 不递增。")
public record UpdateWorkflowTaskDefinitionRequest(
        @Schema(description = "完整工作流定义，当前仅接受 schemaVersion=1。保存接口不会阻止节点 ID、引用任务、依赖边或环错误；保存后应调用校验接口，发布时也会执行同一校验。") @NotNull WorkflowDefinition definition
) { }

package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Target field structure that a user wants to apply to an existing managed physical table. */
@Schema(description = "为 DRAFT 或 DISABLED 模型中当前严格匹配的 MANAGED 物理表生成不可编辑的结构变更计划；冻结真实结构指纹、目标字段、检查和可执行 SQL，但不修改正式字段或物理表。新计划会把旧 PLANNED 计划标为 SUPERSEDED。")
public record CreatePhysicalTableChangePlanRequest(
        @Schema(description = "目标完整字段结构，包含 1 至 500 个字段；已有字段用当前 UUID 保留身份，遗漏即计划删除，新字段 id 为空。服务端据此冻结目标快照、风险、检查和受控 DDL；没有物理结构变化时应直接调用字段保存接口。")
        @NotNull @NotEmpty @Size(max = 500) List<@Valid DataModelFieldInput> fields
) {
}

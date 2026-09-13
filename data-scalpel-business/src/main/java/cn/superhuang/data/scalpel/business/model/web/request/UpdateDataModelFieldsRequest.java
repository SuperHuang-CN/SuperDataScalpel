package cn.superhuang.data.scalpel.business.model.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "整体替换 DRAFT 或 DISABLED 模型的字段定义。请求中保留的字段必须携带原 UUID，遗漏的原字段视为删除；成功时即使只是元数据调整或内容未变化，也会推进一次 schemaVersion 并重新协调质量规则。")
public record UpdateDataModelFieldsRequest(
        @Schema(description = "完整目标字段列表，最多 500 个。空列表允许 DRAFT 或 DISABLED 暂时没有字段，但不能发布；删除被已发布指标保护的字段会被拒绝。已存在且匹配的 MANAGED 物理表只允许直接保存元数据变化，结构变化必须走物理变更计划。")
        @NotNull @Size(max = 500) List<@Valid DataModelFieldInput> fields
) {
}

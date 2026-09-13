package cn.superhuang.data.scalpel.business.dataentry.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

@Schema(description = "为一个尚未绑定表单的现有模型创建唯一的草稿填报表单。")

public record CreateDataEntryFormRequest(
        @Schema(description = "作为填报目标的模型 UUID；创建时只建立表单配置，不修改模型或物理表。")
        @NotNull UUID modelId
) {
}

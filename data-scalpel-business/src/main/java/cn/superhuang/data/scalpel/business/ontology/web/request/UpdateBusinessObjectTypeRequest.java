package cn.superhuang.data.scalpel.business.ontology.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "修改业务对象类型基础资料；不会修改来源、属性、关系或能力定义。")
public record UpdateBusinessObjectTypeRequest(
        @Schema(description = "业务对象显示名称。") @NotBlank @Size(max = 100) String name,
        @Schema(description = "业务建模目录 UUID；为空时位于根目录。") UUID directoryId,
        @Schema(description = "业务负责人或责任部门。") @Size(max = 100) String ownerName,
        @Schema(description = "对象业务含义和适用范围。") @Size(max = 1000) String summary
) {
}

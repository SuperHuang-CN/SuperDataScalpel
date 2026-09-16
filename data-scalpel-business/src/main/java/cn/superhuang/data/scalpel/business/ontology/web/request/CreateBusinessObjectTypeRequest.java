package cn.superhuang.data.scalpel.business.ontology.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "创建业务对象类型的基础资料；来源、属性和关系通过当前定义保存接口维护。")
public record CreateBusinessObjectTypeRequest(
        @Schema(description = "全局唯一技术编码，创建后不可修改。")
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9_]{0,63}") String code,
        @Schema(description = "业务对象显示名称，例如水库、测站或巡检记录。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "业务建模目录 UUID；为空时位于根目录。") UUID directoryId,
        @Schema(description = "业务负责人或责任部门。") @Size(max = 100) String ownerName,
        @Schema(description = "对象的业务含义和适用范围。") @Size(max = 1000) String summary
) {
}

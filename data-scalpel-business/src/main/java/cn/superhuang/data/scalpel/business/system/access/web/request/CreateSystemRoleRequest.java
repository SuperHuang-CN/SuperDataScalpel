package cn.superhuang.data.scalpel.business.system.access.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "创建自定义系统角色；新角色初始不包含任何权限。")

public record CreateSystemRoleRequest(
        @Schema(description = "角色稳定编码；保存时去除首尾空白并转为小写，创建后不可修改。")
        @NotBlank
        @Pattern(regexp = "[A-Za-z][A-Za-z0-9_.-]{0,63}", message = "角色编码以字母开头，只能包含字母、数字、点、下划线和连字符")
        String code,
        @Schema(description = "角色显示名称，最长 100 字符；保存时去除首尾空白。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "角色职责和授权范围说明，最长 500 字符；为空或仅含空白时保存为 null。")
        @Size(max = 500) String description
) {
}

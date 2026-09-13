package cn.superhuang.data.scalpel.business.system.access.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "修改角色显示信息；角色编码和权限集合不由此接口改变，内置角色也允许修改显示名称和说明。")

public record UpdateSystemRoleRequest(
        @Schema(description = "新的角色显示名称，最长 100 字符；保存时去除首尾空白。")
        @NotBlank @Size(max = 100) String name,
        @Schema(description = "新的角色职责和授权范围说明，最长 500 字符；为空或仅含空白时清空。")
        @Size(max = 500) String description
) {
}

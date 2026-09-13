package cn.superhuang.data.scalpel.business.system.access.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.system.access.domain.SystemPermission;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "系统预置权限定义；权限由代码初始化，管理接口只读。")

public record SystemPermissionResponse(
        @Schema(description = "权限 UUID。")
        UUID id,
        @Schema(description = "由代码声明且不可修改的稳定权限编码，Resource 的 Spring Security 授权表达式和登录权限列表均使用该值。")
        String code,
        @Schema(description = "权限所属业务模块，用于管理页面分组。")
        String module,
        @Schema(description = "面向角色授权页面展示的权限中文名称。")
        String name,
        @Schema(description = "权限允许的操作说明。")
        String description,
        @Schema(description = "同级展示顺序，数值越小越靠前。")
        int sortOrder,
        @Schema(description = "权限是否仍由当前代码声明；false 表示历史权限已移除，即使角色映射仍存在也不会进入实际登录或系统 MCP 授权。")
        boolean active,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt,
        @Schema(description = "最后更新时间，ISO-8601 UTC 时间戳。")
        Instant updatedAt
) {
    public static SystemPermissionResponse from(SystemPermission permission) {
        return new SystemPermissionResponse(
                permission.getId(), permission.getCode(), permission.getModule(), permission.getName(), permission.getDescription(),
                permission.getSortOrder(), permission.isActive(), permission.getCreatedAt(), permission.getUpdatedAt()
        );
    }
}

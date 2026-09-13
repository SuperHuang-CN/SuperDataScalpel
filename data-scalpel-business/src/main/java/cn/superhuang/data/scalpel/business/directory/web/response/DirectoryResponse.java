package cn.superhuang.data.scalpel.business.directory.web.response;

import cn.superhuang.data.scalpel.business.directory.domain.Directory;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "一个持久化目录节点的分类属性；不包含子目录、资源列表或权限信息")
public record DirectoryResponse(
        @Schema(description = "目录 UUID") UUID id,
        @Schema(description = "目录所属的独立业务范围；创建后固定") DirectoryScope scope,
        @Schema(description = "上级目录 UUID；顶级目录为空") UUID parentId,
        @Schema(description = "目录显示名称；在同一 scope 和 parentId 下忽略大小写唯一") String name,
        @Schema(description = "同级显示排序值；数值越小越靠前，数值相同时再按名称排序") int sortOrder,
        @Schema(description = "目录用途或内容说明；未填写时为空") String description,
        @Schema(description = "目录创建时间，ISO-8601 UTC 时间戳") Instant createdAt,
        @Schema(description = "目录名称、父节点、排序或说明最后更新时间，ISO-8601 UTC 时间戳") Instant updatedAt
) {
    public static DirectoryResponse from(Directory directory) {
        return new DirectoryResponse(
                directory.getId(),
                directory.getScope(),
                directory.getParentId(),
                directory.getName(),
                directory.getSortOrder(),
                directory.getDescription(),
                directory.getCreatedAt(),
                directory.getUpdatedAt()
        );
    }
}

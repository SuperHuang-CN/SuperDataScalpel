package cn.superhuang.data.scalpel.business.directory.web.response;

import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/** A tree node with direct and descendant resource counts for the current scope. */
@Schema(description = "指定 scope 的递归目录树节点及实时聚合的直属、子树业务资源数量")
public record DirectoryTreeNodeResponse(
        @Schema(description = "目录 UUID") UUID id,
        @Schema(description = "目录所属的独立业务范围；整棵返回树中的节点 scope 相同") DirectoryScope scope,
        @Schema(description = "上级目录 UUID；顶级目录为空") UUID parentId,
        @Schema(description = "目录显示名称；在同一 scope 和 parentId 下忽略大小写唯一") String name,
        @Schema(description = "同级显示排序值；数值越小越靠前") int sortOrder,
        @Schema(description = "目录用途或内容说明；未填写时为空") String description,
        @Schema(description = "业务实体的 directoryId 直接等于当前目录 UUID 的数量，不含后代目录中的资源；统计不会按草稿、启用、停用等业务状态过滤") long directResourceCount,
        @Schema(description = "当前目录与全部后代目录的 directResourceCount 之和；实时聚合且不按业务状态过滤，不是持久化计数器") long resourceCount,
        @Schema(description = "直属子目录，递归包含后代；按 sortOrder、name 稳定排序，无子目录时为空列表") List<DirectoryTreeNodeResponse> children
) {
}

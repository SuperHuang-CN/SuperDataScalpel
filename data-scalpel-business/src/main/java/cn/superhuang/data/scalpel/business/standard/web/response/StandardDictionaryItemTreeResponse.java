package cn.superhuang.data.scalpel.business.standard.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "递归码表节点，包含自身启停状态以及结合码表和全部祖先计算的实际可用状态")
public record StandardDictionaryItemTreeResponse(
        @Schema(description = "码表节点 UUID") UUID id,
        @Schema(description = "父节点 UUID；根节点为空") UUID parentId,
        @Schema(description = "按所属码表 valueType 规范化后的业务码值，在整张码表内唯一；非叶子节点同样可以作为业务值") String code,
        @Schema(description = "码值显示名称") String name,
        @Schema(description = "同一父节点下的零基顺序；服务端维护为连续序号") int sortOrder,
        @Schema(description = "节点自身是否启用；停用父节点不会改写后代节点的该字段") boolean enabled,
        @Schema(description = "码表、当前节点及全部祖先是否同时启用；只有 true 才能作为当前有效值") boolean effectiveEnabled,
        @Schema(description = "码值含义、统计口径或使用说明；未填写时为空") String description,
        @Schema(description = "按 sortOrder、name、code、UUID 稳定排序的直属子节点；无子节点时为空列表") List<StandardDictionaryItemTreeResponse> children,
        @Schema(description = "节点创建时间，ISO-8601 UTC 时间戳") Instant createdAt,
        @Schema(description = "节点最后更新时间，ISO-8601 UTC 时间戳") Instant updatedAt
) {
    public StandardDictionaryItemTreeResponse {
        children = List.copyOf(children);
    }
}

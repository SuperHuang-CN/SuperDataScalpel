package cn.superhuang.data.scalpel.business.standard.web.response;

import cn.superhuang.data.scalpel.business.standard.domain.StandardDictionaryItem;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "单个码表节点的持久化快照；不包含子节点，也不能据此单独判断考虑祖先后的实际可用性")
public record StandardDictionaryItemResponse(
        @Schema(description = "码表节点 UUID") UUID id,
        @Schema(description = "所属码表 UUID") UUID dictionaryId,
        @Schema(description = "父节点 UUID；根节点为空") UUID parentId,
        @Schema(description = "按所属码表 valueType 规范化后的业务码值，在整张码表内唯一；业务数据保存该 code 而不是节点 UUID") String code,
        @Schema(description = "码值显示名称") String name,
        @Schema(description = "同一父节点下的零基顺序；移动、插入或删除节点后由服务端压实") int sortOrder,
        @Schema(description = "节点自身是否启用；实际可用还要求所属码表和全部祖先节点均启用") boolean enabled,
        @Schema(description = "码值含义、统计口径或使用说明；未填写时为空") String description,
        @Schema(description = "节点创建时间，ISO-8601 UTC 时间戳") Instant createdAt,
        @Schema(description = "节点 code、名称、父节点、排序、启停状态或说明最后更新时间，ISO-8601 UTC 时间戳") Instant updatedAt
) {
    public static StandardDictionaryItemResponse from(StandardDictionaryItem item) {
        return new StandardDictionaryItemResponse(
                item.getId(),
                item.getDictionaryId(),
                item.getParentId(),
                item.getCode(),
                item.getName(),
                item.getSortOrder(),
                item.isEnabled(),
                item.getDescription(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}

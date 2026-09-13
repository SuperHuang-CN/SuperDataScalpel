package cn.superhuang.data.scalpel.business.standard.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "码表节点创建、修改、移动或启停命令的结果；返回命令完成后的版本与节点快照")
public record StandardDictionaryItemMutationResponse(
        @Schema(description = "操作完成后的码表业务内容版本；实际无变化时与请求 expectedVersion 相同，否则递增 1") int dictionaryVersion,
        @Schema(description = "创建、修改、移动或启停后的目标节点快照；不包含子树和 effectiveEnabled") StandardDictionaryItemResponse item
) {
}

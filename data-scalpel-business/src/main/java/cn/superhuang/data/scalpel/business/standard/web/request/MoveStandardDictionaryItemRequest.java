package cn.superhuang.data.scalpel.business.standard.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;

import java.util.UUID;

@Schema(description = "将码表节点移动到同一码表中的新父节点及同级位置")
public record MoveStandardDictionaryItemRequest(
        @Schema(description = "客户端最近读取到的码表内容版本；与服务端当前版本不一致时返回 409，避免覆盖并发修改") @Min(1) int expectedVersion,
        @Schema(description = "目标父节点 UUID；为空表示移动为根节点，非空时必须属于同一码表，且不能是当前节点自身或其后代") UUID targetParentId,
        @Schema(description = "移动后在目标同级列表中的零基位置，不能超过目标同级节点数；服务端同时压实新旧父节点下的 sortOrder") @Min(0) int targetIndex
) {
}

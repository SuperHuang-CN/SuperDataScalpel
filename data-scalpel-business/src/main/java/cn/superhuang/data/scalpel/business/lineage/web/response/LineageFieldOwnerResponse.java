package cn.superhuang.data.scalpel.business.lineage.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
@Schema(description = "FIELD 节点所属模型、外部资源或数据服务的展示摘要。")

public record LineageFieldOwnerResponse(
        @Schema(description = "字段所属对象在血缘图内的节点稳定键，可与图节点 id 对应。")
        String key,
        @Schema(description = "字段所属对象的节点类型，通常为模型、外部资源或数据服务。")
        LineageGraphNodeKind kind,
        @Schema(description = "字段所属对象用于主标题展示的名称。")
        String label,
        @Schema(description = "字段所属对象的补充上下文，例如资源类型或业务编码；没有时为空。")
        String subtitle,
        @Schema(description = "字段在所属对象内的展示顺序，数值越小越靠前。")
        int fieldOrder
) {
}

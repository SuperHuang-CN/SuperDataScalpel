package cn.superhuang.data.scalpel.business.standard.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "码表详情及节点、模型字段和模板字段引用统计")
public record StandardDictionaryDetailResponse(
        @Schema(description = "码表基础信息和当前内容版本；节点树需通过 items/tree 接口读取") StandardDictionaryResponse dictionary,
        @Schema(description = "该码表的节点总数，包括所有根节点、非叶子节点和后代节点") long itemCount,
        @Schema(description = "当前直接绑定该码表的真实模型字段数量；大于 0 时码表编码、valueType 及节点 code 受引用保护") long fieldReferenceCount,
        @Schema(description = "当前直接绑定该码表的常用字段模板字段数量；大于 0 时同样触发引用保护") long templateFieldReferenceCount
) {
}

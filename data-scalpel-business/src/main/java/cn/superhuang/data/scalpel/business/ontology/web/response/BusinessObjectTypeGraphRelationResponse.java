package cn.superhuang.data.scalpel.business.ontology.web.response;

import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeDefinition.RelationCardinality;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "本体总览中的规范方向业务关系。一份保存的关系定义只出现一次，反向访问通过同一记录表达。")
public record BusinessObjectTypeGraphRelationResponse(
        @Schema(description = "关系稳定 UUID；未完成的关系配置可能为空。") UUID id,
        @Schema(description = "关系业务编码。") String code,
        @Schema(description = "维护该关系定义的起点对象类型 UUID。") UUID sourceObjectTypeId,
        @Schema(description = "起点对象类型名称。") String sourceObjectTypeName,
        @Schema(description = "终点对象类型 UUID；目标尚未配置时为空，引用失效时保留原 UUID。") UUID targetObjectTypeId,
        @Schema(description = "终点对象类型名称；目标未配置或引用失效时为空。") String targetObjectTypeName,
        @Schema(description = "起点到终点的业务名称。") String forwardName,
        @Schema(description = "起点对象上的访问编码。") String forwardAccessCode,
        @Schema(description = "终点到起点的业务名称。") String reverseName,
        @Schema(description = "终点对象上的反向访问编码。") String reverseAccessCode,
        @Schema(description = "关系业务说明；未填写时为空。") String description,
        @Schema(description = "原定义从起点到终点的数量约束；反向含义需反转。") RelationCardinality cardinality,
        @Schema(description = "起点主来源用于关联的字段 UUID。") UUID sourceFieldId,
        @Schema(description = "起点关联字段名称；字段引用失效或调用者缺少 model.view 权限时为空。") String sourceFieldName,
        @Schema(description = "起点关联字段编码；字段引用失效或调用者缺少 model.view 权限时为空。") String sourceFieldCode,
        @Schema(description = "终点主来源用于关联的字段 UUID。") UUID targetFieldId,
        @Schema(description = "终点关联字段名称；字段引用失效或调用者缺少 model.view 权限时为空。") String targetFieldName,
        @Schema(description = "终点关联字段编码；字段引用失效或调用者缺少 model.view 权限时为空。") String targetFieldCode
) {
}

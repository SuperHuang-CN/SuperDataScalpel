package cn.superhuang.data.scalpel.business.ontology.web.response;

import cn.superhuang.data.scalpel.business.ontology.domain.BusinessObjectTypeDefinition.RelationCardinality;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

@Schema(description = "从一个对象类型可访问的业务关系摘要；INBOUND 代表其他对象类型定义的反向访问。")
public record BusinessObjectRelationResponse(
        @Schema(description = "关系稳定 UUID。") UUID id,
        @Schema(description = "业务编码。") String code,
        @Schema(description = "业务名称。") String name,
        @Schema(description = "当前访问端的关系访问编码。") String accessCode,
        @Schema(description = "相反访问方向的业务名称。") String reverseName,
        @Schema(description = "相反访问端的关系访问编码。") String reverseAccessCode,
        @Schema(description = "关系业务说明；未填写时为空。") String description,
        @Schema(description = "关系原始定义从起点到终点的数量约束；反向访问时需反转理解。") RelationCardinality cardinality,
        @Schema(description = "访问方向：OUTBOUND 为定义正向，INBOUND 为定义反向。") String direction,
        @Schema(description = "原定义起点类型 UUID，也是关系定义的维护位置。") UUID sourceObjectTypeId,
        @Schema(description = "原定义起点类型名称。") String sourceObjectTypeName,
        @Schema(description = "原定义终点类型 UUID。") UUID targetObjectTypeId,
        @Schema(description = "原定义终点类型名称。") String targetObjectTypeName
) {
}

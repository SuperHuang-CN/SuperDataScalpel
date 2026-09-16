package cn.superhuang.data.scalpel.business.ontology.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "业务对象类型库最近保存结构的轻量拓扑快照。不包含业务对象记录、完整属性定义或实时来源健康状态。")
public record BusinessObjectTypeGraphResponse(
        @Schema(description = "全部业务对象类型的轻量摘要。") List<BusinessObjectTypeGraphNodeResponse> nodes,
        @Schema(description = "按原始定义方向返回的业务关系；一份关系只返回一次。") List<BusinessObjectTypeGraphRelationResponse> relations,
        @Schema(description = "本次从管理库读取拓扑的时间。") Instant queriedAt
) {
}

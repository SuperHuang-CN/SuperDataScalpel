package cn.superhuang.data.scalpel.business.service.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "批量查询数据服务关联模型中多个字段的上下游字段血缘。")

public record QueryDataServiceFieldLineageRequest(
        @Schema(description = "属于该服务关联模型的字段 UUID 列表，最多 50 项；省略时选择按模型字段顺序排列的前 20 个已暴露字段，空列表返回空焦点集合，重复 UUID 会去重；任一 UUID 不属于关联模型时整次返回 404。未被服务暴露但属于关联模型的显式字段会返回 hasLineage=false 和原因告警。")
        @Size(max = 50, message = "一次最多查询 50 个字段") List<UUID> fieldIds,
        @Schema(description = "从所选字段向上下游跨越的任务转换深度，只支持 1 或 2；省略时为 2。")
        Integer depth
) {
}

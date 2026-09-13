package cn.superhuang.data.scalpel.business.model.web.request;

import cn.superhuang.data.scalpel.business.lineage.web.request.LineageDirection;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

@Schema(description = "批量查询当前模型指定字段的未退役任务血缘快照；返回合并图及逐字段焦点摘要。")
public record QueryModelFieldLineageRequest(
        @Schema(description = "属于当前模型的字段 UUID，最多 50 个；省略时选择按字段顺序排列的前 20 个，空数组表示不聚焦任何字段，重复 UUID 会去重；任一 UUID 不属于当前模型时整次返回 404。") @Size(max = 50, message = "一次最多查询 50 个字段") List<UUID> fieldIds,
        @Schema(description = "血缘方向：UPSTREAM 上游、DOWNSTREAM 下游或 BOTH 双向；省略时为 BOTH。") LineageDirection direction,
        @Schema(description = "跨越的任务转换深度，只允许 1 或 2；省略时为 2。") Integer depth
) {
}

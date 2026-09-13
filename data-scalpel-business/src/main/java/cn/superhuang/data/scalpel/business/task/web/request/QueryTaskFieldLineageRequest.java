package cn.superhuang.data.scalpel.business.task.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "批量查询任务当前未退役血缘快照中一条输出流的指定字段；响应图最多 200 个节点和 600 条关系，超过时通过 graph.truncated 与 warnings 说明。")
public record QueryTaskFieldLineageRequest(
        @Schema(description = "flows 中的稳定输出流键；为空或全空白时选择按 flowKey 排序的第一条流，未知值返回 404。") String flowKey,
        @Schema(description = "该流的输出字段稳定键，最多 50 项。省略时选择按 sortOrder 排列的前 20 个字段；空数组明确表示不聚焦任何字段；重复键会去重，任一未知键使整次返回 404。") @Size(max = 50, message = "一次最多查询 50 个字段") List<String> outputFieldKeys
) {
}

package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.contract.execution.CanvasTrialSpec;
import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "提交当前未保存的 SPARK_CANVAS 草稿进行节点试运行。任务必须处于 DRAFT 或 DISABLED、至少保存过一次定义且没有活动运行；服务端只执行目标节点及其上游闭包，不保存草稿、不发布血缘，也不执行任何 Output 节点。")
public record CanvasTrialRunRequest(
        @Schema(description = "草稿所基于的当前已保存 Canvas 定义版本，从 1 开始；必须与服务端版本完全一致，定义在读取后发生变化时返回 409。") @Min(1) int baseDefinitionVersion,
        @Schema(description = "界面内存中的完整 Canvas 草稿。服务端会升级兼容协议、裁剪为目标节点及上游闭包并执行完整试运行校验；该内容不会写回任务定义。") @NotNull @Valid CanvasDefinition definition,
        @Schema(description = "definition 中要试运行的 Input 或 Processor 节点规范 UUID 字符串；Output 节点、未知节点和非 UUID 值均被拒绝。") @NotBlank @Size(max = 100) String targetNodeId,
        @Schema(description = "目标节点输出中要预览的逻辑表名，必须属于该节点推导出的表 Schema。") @NotBlank @Size(max = 255) String tableName,
        @Schema(description = "要从目标逻辑表投影的唯一字段名列表，顺序决定预览列顺序；必须包含 1 至 4096 项并与推导 Schema 匹配。成功预览最多返回 100 行、4 MiB UTF-8 行数据。")
        @NotNull @Size(min = 1, max = CanvasTrialSpec.MAX_COLUMNS)
        List<@NotBlank @Size(max = 255) String> columnNames
) {
    public CanvasTrialRunRequest {
        columnNames = columnNames == null ? null : List.copyOf(columnNames);
    }
}

package cn.superhuang.data.scalpel.business.task.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "完整替换任务名称、目录、说明和绑定引擎；不改变任务类型、类型专属定义或已有运行快照。已发布任务不能更换绑定引擎。")
public record UpdateDataTaskRequest(
        @Schema(description = "任务显示名称，去除首尾空白后不能为空且最长 100 个字符。") @NotBlank @Size(max = 100) String name,
        @Schema(description = "TASK 范围目录 UUID，必须指向现有目录；为空表示移到未分类。") UUID directoryId,
        @Schema(description = "任务业务目的、输入输出或运维说明，去除首尾空白后最长 1000 个字符；为空或全空白时清除。") @Size(max = 1000) String description,
        @Schema(description = "计算引擎 UUID。需要计算引擎的 Spark/质检任务必填且必须存在；LOCAL_SQL 与 WORKFLOW 必须为空。已发布任务不能改变该值，已有运行始终保留创建时固化的引擎。") UUID computeEngineId
) {
    public UpdateDataTaskRequest(String name, UUID directoryId, String description) {
        this(name, directoryId, description, null);
    }
}

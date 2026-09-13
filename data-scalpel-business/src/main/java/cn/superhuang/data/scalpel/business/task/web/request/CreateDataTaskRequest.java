package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

@Schema(description = "创建 DRAFT 数据任务根记录；任务类型创建后不可切换，类型专属定义需随后通过对应接口保存，发布也需显式执行。")
public record CreateDataTaskRequest(
        @Schema(description = "任务显示名称，去除首尾空白后不能为空且最长 100 个字符。") @NotBlank @Size(max = 100) String name,
        @Schema(description = "TASK 范围目录 UUID，必须指向现有目录；为空表示未分类。目录只用于组织，不构成执行权限。") UUID directoryId,
        @Schema(description = "任务定义家族，决定后续定义结构、发布校验和执行方式，创建后不可修改。") @NotNull TaskType type,
        @Schema(description = "任务业务目的、输入输出或运维说明，去除首尾空白后最长 1000 个字符；为空或全空白时不保存。") @Size(max = 1000) String description,
        @Schema(description = "计算引擎 UUID。SPARK_CANVAS、SPARK_STREAMING_CANVAS、SPARK_MODEL_QUALITY、SPARK_JAR 和 SPARK_STREAMING_JAR 必填且必须指向现有引擎；LOCAL_SQL 与 WORKFLOW 必须为空。系统不会为缺失值自动选择默认引擎。") UUID computeEngineId
) {
    public CreateDataTaskRequest(String name, UUID directoryId, TaskType type, String description) {
        this(name, directoryId, type, description, null);
    }
}

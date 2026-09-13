package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "数据任务根记录、当前类型专属定义状态和关键模型引用摘要；不包含完整定义或任何一次运行状态。")
public record DataTaskResponse(
        @Schema(description = "任务根记录 UUID，用于查询详情、维护定义、发布和创建运行。") UUID id,
        @Schema(description = "任务当前显示名称；运行历史可能保存创建运行时的名称快照。") String name,
        @Schema(description = "TASK 范围目录 UUID；未分类时为空。目录只用于组织，不构成执行权限或数据隔离。") UUID directoryId,
        @Schema(description = "任务定义家族，创建后不可修改；决定使用哪种定义接口、是否需要计算引擎以及批处理或流式执行协议。") TaskType type,
        @Schema(description = "任务生命周期：DRAFT 不能正式运行，PUBLISHED 可按类型运行或调度，DISABLED 已停用且不接受新正式运行；不表示某次运行状态。") TaskStatus status,
        @Schema(description = "任务业务目的、输入输出或运维说明；未填写时为空，不参与执行。") String description,
        @Schema(description = "任务固定绑定的计算引擎 UUID；LOCAL_SQL 和 WORKFLOW 不使用计算引擎并为空，其他类型未绑定时也为空且不能发布。") UUID computeEngineId,
        @Schema(description = "绑定计算引擎当前显示名称；未绑定或引擎已删除时为空。运行实际路由使用 TaskRun 固化的引擎快照。") String computeEngineName,
        @Schema(description = "当前类型定义的摘要配置标记：LOCAL_SQL、WORKFLOW、Canvas 和模型质检表示定义记录存在；Spark JAR 只有已经关联当前 JAR 时为 true。true 仍不保证满足发布或运行条件。") boolean definitionConfigured,
        @Schema(description = "当前类型专属定义版本；完全没有定义记录时为空。Spark JAR 可在 definitionConfigured=false 时已有参数/在线源码定义版本；各类型对无变化保存是否递增以其定义接口说明为准。") Integer definitionVersion,
        @Schema(description = "仅 LOCAL_SQL 摘要直接返回的输出模型 UUID；其他任务即使有输出模型也为空，需查询 model-relations 或完整定义。") UUID outputModelId,
        @Schema(description = "LOCAL_SQL 输出模型当前显示名称；outputModelId 为空或模型已删除时为空。") String outputModelName,
        @Schema(description = "模型质量任务的目标模型 UUID；其他任务类型时为空。") UUID qualityTargetModelId,
        @Schema(description = "质量目标模型当前显示名称；非质检任务或模型已删除时为空。") String qualityTargetModelName,
        @Schema(description = "任务根记录创建时间，ISO-8601 UTC 时间戳。") Instant createdAt,
        @Schema(description = "任务名称、目录、说明、绑定引擎或生命周期最后变更时间，ISO-8601 UTC 时间戳；类型专属定义有自己的版本和更新时间。") Instant updatedAt
) {

    public static DataTaskResponse from(
            DataTask task,
            String computeEngineName,
            boolean definitionConfigured,
            Integer definitionVersion,
            UUID outputModelId,
            String outputModelName,
            UUID qualityTargetModelId,
            String qualityTargetModelName
    ) {
        return new DataTaskResponse(
                task.getId(), task.getName(), task.getDirectoryId(), task.getType(), task.getStatus(),
                task.getDescription(), task.getComputeEngineId(), computeEngineName,
                definitionConfigured, definitionVersion, outputModelId, outputModelName,
                qualityTargetModelId, qualityTargetModelName,
                task.getCreatedAt(), task.getUpdatedAt()
        );
    }

    public static DataTaskResponse from(
            DataTask task,
            String computeEngineName,
            boolean definitionConfigured,
            Integer definitionVersion,
            UUID outputModelId,
            String outputModelName
    ) {
        return from(task, computeEngineName, definitionConfigured, definitionVersion,
                outputModelId, outputModelName, null, null);
    }

    public static DataTaskResponse from(
            DataTask task,
            boolean definitionConfigured,
            Integer definitionVersion,
            UUID outputModelId,
            String outputModelName
    ) {
        return from(task, null, definitionConfigured, definitionVersion, outputModelId, outputModelName,
                null, null);
    }
}

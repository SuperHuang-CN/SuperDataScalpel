package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "引用指定模型的任务及引用位置摘要。")

public record ModelRelatedTaskResponse(
        @Schema(description = "所属任务 UUID。")
        UUID taskId,
        @Schema(description = "任务名称。")
        String taskName,
        @Schema(description = "任务类型，决定定义和执行协议。")
        TaskType taskType,
        @Schema(description = "任务自身状态，与某次运行状态不同。")
        TaskStatus taskStatus,
        @Schema(description = "查询时引用该模型的当前任务定义版本；历史运行仍保留其提交时版本。")
        int definitionVersion,
        @Schema(description = "该模型相对任务承担的输入或输出角色集合。")
        List<ModelTaskRelationRole> roles,
        @Schema(description = "该模型在任务定义中的全部引用位置。")
        List<TaskModelReferenceLocationResponse> locations,
        @Schema(description = "任务根记录最后更新时间，ISO-8601 UTC 时间戳；类型专属定义或模型关系变化不一定同步更新该时间。")
        Instant updatedAt
) {
}

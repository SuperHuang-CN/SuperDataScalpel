package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.task.domain.TaskStatus;
import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "直接引用该数据源，或通过存储在该数据源上的模型间接引用它的任务摘要；引用来自任务当前保存定义，不表示某次运行实际访问记录。")
public record DataSourceRelatedTaskResponse(
        @Schema(description = "引用该数据源的任务 UUID。") UUID taskId,
        @Schema(description = "引用该数据源的任务显示名称。") String taskName,
        @Schema(description = "任务定义类型，决定 locations 中可出现的资源种类、位置键和实际执行方式。") TaskType taskType,
        @Schema(description = "任务当前生命周期：DRAFT 草稿、PUBLISHED 已发布、DISABLED 已停用。该值不表示某次运行状态。") TaskStatus taskStatus,
        @Schema(description = "当前类型专属任务定义的内容版本，从 1 开始并在定义实际变化时递增；0 表示尚未保存可识别的类型定义。") int definitionVersion,
        @Schema(description = "该数据源在任务中的输入或输出角色，可同时包含两者。") List<DataSourceTaskRelationRole> roles,
        @Schema(description = "任务引用数据源的方式，可同时包含直接引用和经模型间接引用。") List<DataSourceRelationKind> relationKinds,
        @Schema(description = "当前筛选条件下，任务定义中引用该数据源的全部已识别位置；同一位置可因 READ_WRITE 同时出现 INPUT 和 OUTPUT 两项。") List<DataSourceTaskReferenceLocationResponse> locations,
        @Schema(description = "任务最后更新时间，ISO-8601 UTC 时间戳。") Instant updatedAt
) {
}

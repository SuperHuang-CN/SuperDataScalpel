package cn.superhuang.data.scalpel.business.task.web.request;

import cn.superhuang.data.scalpel.contract.execution.StreamingCheckpointMode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "SPARK_STREAMING_JAR 启动时选择 Checkpoint 状态来源；该任务类型必须提交请求体。SPARK_STREAMING_CANVAS 可省略请求体，其 Checkpoint 由 Canvas 定义版本对应的部署管理。")
public record StartStreamingTaskRequest(
        @Schema(description = "JAR Checkpoint 模式：CONTINUE 复用最新正式部署的 Checkpoint；同定义版本时复用该部署，不同定义版本时新建指向来源部署的记录。没有历史部署时拒绝 CONTINUE。FRESH 使用新代次和隔离前缀，不读取旧状态。已有活动部署时本值不触发重建。") @NotNull StreamingCheckpointMode checkpointMode
) {
}

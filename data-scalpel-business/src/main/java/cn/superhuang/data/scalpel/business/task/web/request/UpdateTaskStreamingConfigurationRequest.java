package cn.superhuang.data.scalpel.business.task.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Schema(description = "修改实时 Canvas 当前定义中唯一无界输入节点的微批触发间隔；任务存在 STARTING、RUNNING 或 STOPPING 部署时拒绝修改。")
public record UpdateTaskStreamingConfigurationRequest(
        @Schema(description = "Spark Structured Streaming 微批触发间隔，单位秒，闭区间 1 到 300；保存后会更新 Canvas 定义内容及其定义版本。") @Min(1) @Max(300) int triggerIntervalSeconds
) {
}

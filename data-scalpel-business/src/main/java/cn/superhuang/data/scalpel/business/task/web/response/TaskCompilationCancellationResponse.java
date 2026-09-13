package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "活动编译取消请求的受理结果；只有 Task Engine 找到活动 requestId 时才返回，且不保证响应时编译线程和 Spark Job 已完全停止。")

public record TaskCompilationCancellationResponse(
        @Schema(description = "编译请求 UUID。")
        UUID requestId,
        @Schema(description = "当前固定返回 CANCEL_REQUESTED，表示协作式取消信号已设置；不等同于编译最终结果或任务运行状态。")
        String state
) {
}

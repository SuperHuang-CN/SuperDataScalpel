package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
/** Counts of persisted notification records and suppression decisions for this incident. */
@Schema(description = "告警通知摘要。")
public record AlertNotificationSummary(
        @Schema(description = "为该告警持久化的个人站内通知总数；站内通知落库即对接收人可见，不使用 Webhook 投递状态。")
        long inApp,
        @Schema(description = "Webhook 状态为 PENDING 或 SENDING 的投递数量；SENDING 请求可能仍在途。")
        long pending,
        @Schema(description = "Webhook 状态为 SENT 的成功投递数量。")
        long sent,
        @Schema(description = "Webhook 状态为 FAILED 的最终失败投递数量。")
        long failed,
        @Schema(description = "Webhook 状态为 SUPPRESSED，加上因静默或冷却而只记录抑制动作、未创建投递记录的通知数量。")
        long suppressed
) {}

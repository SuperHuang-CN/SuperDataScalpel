package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.operations.domain.*;
import java.time.Instant;
import java.util.UUID;
@Schema(description = "一条告警 Webhook 投递的尝试状态和结果；站内通知不使用此记录。")
public record AlertDeliveryResponse(
        @Schema(description = "告警投递记录 UUID。")
        UUID id,
        @Schema(description = "本次投递所属告警事件 UUID。")
        UUID incidentId,
        @Schema(description = "接收本次投递的 Webhook 渠道 UUID。")
        UUID channelId,
        @Schema(description = "告警通知渠道名称。")
        String channelName,
        @Schema(description = "投递事件类型：TRIGGERED 新异常，RECOVERED 持续条件恢复，TEST 用户发起的渠道测试；人工确认和关闭不产生独立事件类型。")
        AlertEventType eventType,
        @Schema(description = "Webhook 投递状态：PENDING 待发送，SENDING 发送中，SENT 成功，FAILED 失败，SUPPRESSED 因静默策略未发送。")
        AlertDeliveryStatus status,
        @Schema(description = "该稳定 deliveryId 已实际发起的 Webhook 请求次数；网络重试会递增，尚未发送时为 0。")
        int attempts,
        @Schema(description = "Webhook 最近一次返回的 HTTP 状态码；尚未发送或连接阶段失败时为空。")
        Integer httpStatus,
        @Schema(description = "最近一次 Webhook 请求耗时，单位毫秒；尚未发送时为空。")
        Long durationMillis,
        @Schema(description = "最近一次安全错误摘要；没有错误时为空。")
        String lastError,
        @Schema(description = "PENDING 状态的下一次尝试时间，ISO-8601 UTC 时间戳；自动重试依次至少等待 5、30、120、600、1800 秒，Retry-After 最多采用 1800 秒；无需再尝试时为空。")
        Instant nextAttemptAt,
        @Schema(description = "接收端首次返回 2xx 并将状态置为 SENT 的时间，ISO-8601 UTC 时间戳；未成功发送时为空。")
        Instant sentAt,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt
) {
    public static AlertDeliveryResponse from(AlertDelivery d, String name) {
        return new AlertDeliveryResponse(d.getId(), d.getIncidentId(), d.getChannelId(), name, d.getEventType(), d.getStatus(),
                d.getAttempts(), d.getHttpStatus(), d.getDurationMillis(), d.getLastError(), d.getNextAttemptAt(), d.getSentAt(), d.getCreatedAt());
    }
}

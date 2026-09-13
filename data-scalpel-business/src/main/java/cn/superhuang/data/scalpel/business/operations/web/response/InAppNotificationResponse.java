package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.operations.domain.*;
import java.time.Instant;
import java.util.UUID;
@Schema(description = "当前用户收到的一条站内告警通知及已读状态。")
public record InAppNotificationResponse(
        @Schema(description = "站内通知 UUID。")
        UUID id,
        @Schema(description = "通知关联的告警事件 UUID。")
        UUID incidentId,
        @Schema(description = "触发通知的监控规则类型。")
        AlertRuleType ruleType,
        @Schema(description = "通知事件类型：TRIGGERED 告警触发或静默到期提醒，RECOVERED 持续条件恢复；站内通知不会产生 TEST。")
        AlertEventType eventType,
        @Schema(description = "面向接收人的告警摘要。")
        String summary,
        @Schema(description = "当前用户首次标记已读的时间，ISO-8601 UTC 时间戳；尚未读取时为空，重复标记不会改写。")
        Instant readAt,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt
) {
    public static InAppNotificationResponse from(InAppNotification n) {
        return new InAppNotificationResponse(n.getId(), n.getIncidentId(), n.getRuleType(), n.getEventType(), n.getSummary(), n.getReadAt(), n.getCreatedAt());
    }
}

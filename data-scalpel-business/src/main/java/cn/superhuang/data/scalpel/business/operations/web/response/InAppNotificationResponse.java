package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.operations.domain.*;
import java.time.Instant;
import java.util.UUID;
public record InAppNotificationResponse(UUID id, UUID incidentId, AlertRuleType ruleType, AlertEventType eventType,
    String summary, Instant readAt, Instant createdAt) {
    public static InAppNotificationResponse from(InAppNotification n) {
        return new InAppNotificationResponse(n.getId(), n.getIncidentId(), n.getRuleType(), n.getEventType(), n.getSummary(), n.getReadAt(), n.getCreatedAt());
    }
}

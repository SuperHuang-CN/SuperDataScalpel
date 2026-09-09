package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.operations.domain.*;
import java.time.Instant;
import java.util.UUID;
public record AlertDeliveryResponse(UUID id, UUID incidentId, UUID channelId, String channelName, AlertEventType eventType,
    AlertDeliveryStatus status, int attempts, Integer httpStatus, Long durationMillis, String lastError,
    Instant nextAttemptAt, Instant sentAt, Instant createdAt) {
    public static AlertDeliveryResponse from(AlertDelivery d, String name) {
        return new AlertDeliveryResponse(d.getId(), d.getIncidentId(), d.getChannelId(), name, d.getEventType(), d.getStatus(),
                d.getAttempts(), d.getHttpStatus(), d.getDurationMillis(), d.getLastError(), d.getNextAttemptAt(), d.getSentAt(), d.getCreatedAt());
    }
}

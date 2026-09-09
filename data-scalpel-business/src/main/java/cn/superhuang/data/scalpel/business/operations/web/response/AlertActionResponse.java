package cn.superhuang.data.scalpel.business.operations.web.response;
import cn.superhuang.data.scalpel.business.operations.domain.AlertAction;
import java.time.Instant;
import java.util.UUID;
public record AlertActionResponse(UUID id, String action, String actorName, String reason, Instant untilAt, Instant createdAt) {
    public static AlertActionResponse from(AlertAction a) { return new AlertActionResponse(a.getId(), a.getAction(), a.getActorName(), a.getReason(), a.getUntilAt(), a.getCreatedAt()); }
}

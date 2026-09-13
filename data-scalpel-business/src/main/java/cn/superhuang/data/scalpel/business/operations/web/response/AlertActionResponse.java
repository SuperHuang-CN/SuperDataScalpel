package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.operations.domain.AlertAction;
import java.time.Instant;
import java.util.UUID;
@Schema(description = "一条告警事件的触发、恢复、确认、关闭、静默、静默到期、解除静默或通知抑制历史记录。")
public record AlertActionResponse(
        @Schema(description = "告警动作记录 UUID。")
        UUID id,
        @Schema(description = "动作代码，例如 TRIGGERED、RECOVERED、ENDED、ACKNOWLEDGED、CLOSED、SILENCED、UNSILENCED、SILENCE_EXPIRED 或 NOTIFICATION_SUPPRESSED。")
        String action,
        @Schema(description = "执行该告警动作的用户显示名称；系统自动动作固定为“系统”。")
        String actorName,
        @Schema(description = "人工填写的原因或系统记录的触发、恢复、结束及抑制原因；没有时为空。")
        String reason,
        @Schema(description = "SILENCED 动作的静默截止时间，ISO-8601 UTC 时间戳；其他动作类型为空。")
        Instant untilAt,
        @Schema(description = "创建时间，ISO-8601 UTC 时间戳。")
        Instant createdAt
) {
    public static AlertActionResponse from(AlertAction a) { return new AlertActionResponse(a.getId(), a.getAction(), a.getActorName(), a.getReason(), a.getUntilAt(), a.getCreatedAt()); }
}

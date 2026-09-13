package cn.superhuang.data.scalpel.business.operations.web.request;

import io.swagger.v3.oas.annotations.media.Schema;import jakarta.validation.constraints.*;
import java.time.Instant;
@Schema(description = "在指定未来时间前，按规则类型和对象范围暂停后续通知；同对象同类型的其他活动事件也会受影响，同时保留条件观测和事件状态。")
public record SilenceAlertRequest(
        @Schema(description = "静默截止时间，必须晚于当前时间。")
        @NotNull @Future Instant untilAt,
        @Schema(description = "静默原因，写入告警动作历史。")
        @NotBlank @Size(max=1000) String reason
) {}

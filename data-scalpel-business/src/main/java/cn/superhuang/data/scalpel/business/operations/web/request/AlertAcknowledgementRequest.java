package cn.superhuang.data.scalpel.business.operations.web.request;

import io.swagger.v3.oas.annotations.media.Schema;import jakarta.validation.constraints.Size;
@Schema(description = "人工确认已知悉一条开放告警；确认不会声明监控条件已经恢复。")
public record AlertAcknowledgementRequest(
        @Schema(description = "本次确认的可选处理说明。")
        @Size(max=1000) String reason
) {}

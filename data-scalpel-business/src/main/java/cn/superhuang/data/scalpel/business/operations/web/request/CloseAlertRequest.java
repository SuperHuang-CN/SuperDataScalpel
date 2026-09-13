package cn.superhuang.data.scalpel.business.operations.web.request;

import io.swagger.v3.oas.annotations.media.Schema;import jakarta.validation.constraints.*;
@Schema(description = "人工关闭一条告警事件并记录关闭原因；不修改监控规则。")
public record CloseAlertRequest(
        @Schema(description = "必填的人工关闭原因，写入告警动作历史。")
        @NotBlank @Size(max=1000) String reason
) {}

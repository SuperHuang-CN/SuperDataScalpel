package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import java.util.UUID;
@Schema(description = "可被告警规则选择为站内通知接收人的启用用户。")
public record AlertRecipientResponse(
        @Schema(description = "系统用户 UUID。")
        UUID id,
        @Schema(description = "接收人的系统登录名。")
        String username,
        @Schema(description = "用户显示名称。")
        String displayName
) {}

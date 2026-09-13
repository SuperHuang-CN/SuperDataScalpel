package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "当前用户尚未阅读的站内通知数量。")
public record UnreadNotificationCountResponse(
        @Schema(description = "当前用户尚未标记为已读的站内告警通知数量。")
        long unreadCount
) {}

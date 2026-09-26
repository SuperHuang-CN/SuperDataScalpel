package cn.superhuang.data.scalpel.business.task.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Admin Java 提示连接凭证；仅用于一次 WebSocket 握手，不包含 TaskEngine 内部令牌。")
public record JavaLanguageTicketResponse(
        @Schema(description = "同一 Admin 服务下的 WebSocket 相对路径。") String path,
        @Schema(description = "一次性子协议凭证，作为 WebSocket protocols 的第二项发送；不得放入 URL 或日志。") String ticket,
        @Schema(description = "凭证有效截止时间；签发后 30 秒内且只能使用一次。") Instant expiresAt) {}

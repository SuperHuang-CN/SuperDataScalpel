package cn.superhuang.data.scalpel.business.dsh.web.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;
@Schema(description = "消息持久化接收凭证；接收成功不代表模型已完成执行")
public record DshMessageReceiptResponse(
        @Schema(description = "当前会话 UUID") UUID sessionId,
        @Schema(description = "客户端消息 UUID") UUID messageId,
        @Schema(description = "消息是否已持久化接收") boolean accepted,
        @Schema(description = "是否已接收过同一消息；为 true 时没有再次入队") boolean duplicate) {}

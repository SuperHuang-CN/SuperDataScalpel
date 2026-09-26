package cn.superhuang.data.scalpel.business.task.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

@Schema(description = "申请在线 Java 编辑长连接的一次性凭证，不保存或执行源码。")
public record CreateJavaLanguageTicketRequest(
        @NotNull @Schema(description = "当前独立编辑页面生成的 UUID；短暂断线重连复用，不与其他页面共享。", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID editorSessionId) {}

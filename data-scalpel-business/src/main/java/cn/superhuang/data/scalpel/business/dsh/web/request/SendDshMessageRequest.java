package cn.superhuang.data.scalpel.business.dsh.web.request;
import java.util.UUID;
import java.util.List;
import jakarta.validation.constraints.*;
import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Schema(description = "用户聊天消息；文字和附件至少提供一种")
public record SendDshMessageRequest(
        @NotNull @Schema(description = "客户端消息 UUID；同一标识只能对应相同文字和附件，用于避免重复发送") UUID clientMessageId,
        @Schema(description = "用户文字，默认空；有附件时允许不输入文字") String text,
        @Size(max = 5) @Schema(description = "已上传到当前会话的附件 UUID，按显示顺序，默认空；最多 5 个且不得重复") List<@NotNull UUID> attachmentIds) {
    public SendDshMessageRequest {
        text = text == null ? "" : text;
        attachmentIds = attachmentIds == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(attachmentIds));
    }
    public SendDshMessageRequest(UUID clientMessageId, String text) { this(clientMessageId, text, List.of()); }
    @AssertTrue(message = "请输入文字或添加附件，且附件不能重复") @JsonIgnore
    public boolean isContentValid() {
        return (!text.isBlank() || !attachmentIds.isEmpty()) && new java.util.HashSet<>(attachmentIds).size() == attachmentIds.size();
    }
}

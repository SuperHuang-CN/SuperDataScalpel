package cn.superhuang.data.scalpel.business.dsh.web.response;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import tools.jackson.databind.JsonNode;
@Schema(description = "一条已持久化的助手会话消息，附件元信息不含原始字节或内部路径")
public record DshMessageResponse(
        @Schema(description = "原生消息稳定标识；用户消息为客户端生成的 UUID") String id,
        @Schema(description = "消息角色：user 用户，assistant 助手，tool 工具结果", allowableValues = {"user", "assistant", "tool"}) String role,
        @Schema(description = "按原生顺序展示的内容块。text 含 text 文字；tool-call 含 id、name、arguments；tool-result 含 toolCallId、content、isError。附件的模型内部描述不混入用户原文") List<JsonNode> content,
        @Schema(description = "原生会话事件序号，按升序排序") long sourceSeq,
        @Schema(description = "用户随消息发送的附件列表；旧消息为空或缺省，最多 5 项") List<DshAttachmentResponse> attachments) {}

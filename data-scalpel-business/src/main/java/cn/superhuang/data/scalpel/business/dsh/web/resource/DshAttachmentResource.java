package cn.superhuang.data.scalpel.business.dsh.web.resource;

import cn.superhuang.data.scalpel.business.dsh.service.*;
import cn.superhuang.data.scalpel.business.dsh.web.request.UploadDshAttachmentRequest;
import cn.superhuang.data.scalpel.business.dsh.web.response.DshAttachmentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/dsh/sessions/{sessionId}/attachments")
@Tag(name = "DSH 助手附件", description = "当前用户会话附件上传和读取；不向系统 MCP 开放")
public class DshAttachmentResource {
    private final DshAttachmentService attachments;
    private final DshIdentityService identities;
    public DshAttachmentResource(DshAttachmentService a, DshIdentityService i) { attachments = a; identities = i; }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "上传聊天附件", description = "仅向当前用户未归档会话保存附件，不启动模型；客户端将返回的附件 ID 随消息发送。单文件最多 5 MiB，文本或 Excel 解析内容最多 1 MiB。Excel 使用缓存公式值，不执行公式或宏。跨用户 404，归档 409，不支持的类型 415，无法解析 422，超限 413；同一上传 UUID 和内容可安全重试。")
    public DshAttachmentResponse upload(Authentication authentication,
            @Parameter(description = "当前用户所属会话 UUID") @PathVariable UUID sessionId,
            @Valid @RequestBody UploadDshAttachmentRequest request) {
        return attachments.upload(identities.require(authentication).userId(), sessionId, request);
    }
    @GetMapping("/{id}")
    @Operation(summary = "下载聊天附件", description = "以原文件的媒体类型和文件名返回原始字节，最多 5 MiB；空文本文件可以返回空字节。仅所属用户、所属会话可读取，归档后仍可读取；未知或跨用户附件返回 404。使用认证请求，不接受公开文件地址。")
    public ResponseEntity<byte[]> download(Authentication authentication,
            @Parameter(description = "当前用户所属会话 UUID") @PathVariable UUID sessionId,
            @Parameter(description = "上传时返回的附件 UUID") @PathVariable UUID id) {
        return attachments.download(identities.require(authentication).userId(), sessionId, id);
    }
}

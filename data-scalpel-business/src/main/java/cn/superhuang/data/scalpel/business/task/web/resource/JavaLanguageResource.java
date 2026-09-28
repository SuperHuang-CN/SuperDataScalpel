package cn.superhuang.data.scalpel.business.task.web.resource;

import cn.superhuang.data.scalpel.business.task.service.JavaLanguageTicketService;
import cn.superhuang.data.scalpel.business.task.web.request.CreateJavaLanguageTicketRequest;
import cn.superhuang.data.scalpel.business.task.web.response.JavaLanguageTicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/tasks/{taskId}/java-language-tickets")
@Tag(name = "在线 Java 代码提示")
public class JavaLanguageResource {
    private final JavaLanguageTicketService tickets;
    public JavaLanguageResource(JavaLanguageTicketService tickets) { this.tickets = tickets; }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('task.update')")
    @Operation(summary = "申请代码提示连接", description = "验证 Spark JAR 任务后签发 30 秒有效的一次性 WebSocket 子协议凭证。浏览器连接同一 Admin，Admin 持续转发到 TaskEngine。不保存、编译或运行源码。无权限返回 403、任务不存在 404、类型不匹配 409、申请容量满 429。")
    public JavaLanguageTicketResponse create(@Parameter(description = "Spark JAR 任务 UUID") @PathVariable UUID taskId,
                                            @Valid @RequestBody CreateJavaLanguageTicketRequest request, Authentication authentication) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "请重新登录");
        String userId = jwt.getClaimAsString("userId");
        String owner = userId == null ? "subject:" + jwt.getSubject() : "user:" + userId;
        return tickets.create(taskId, request.editorSessionId(), owner, jwt.getExpiresAt());
    }
}

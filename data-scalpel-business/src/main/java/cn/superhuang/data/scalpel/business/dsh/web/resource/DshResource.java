package cn.superhuang.data.scalpel.business.dsh.web.resource;

import cn.superhuang.data.scalpel.business.dsh.service.*;
import cn.superhuang.data.scalpel.business.dsh.web.request.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import tools.jackson.databind.JsonNode;
import java.util.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.dsh.web.response.*;
@RestController
@RequestMapping("/api/v1/dsh")
@Tag(name="DSH 助手接入",description="当前用户的工作区及会话控制；不向系统 MCP 开放")
public class DshResource {
    private final DshService service;
    private final DshIdentityService identities;
    private final DshEventService events;
    public DshResource(DshService s,DshIdentityService i,DshEventService e) {service=s;identities=i;events=e;}
    private UUID owner(Authentication a) {return identities.require(a).userId();}
    @GetMapping("/capabilities") public JsonNode capabilities(Authentication a) {owner(a);return service.capabilities();}
    @PostMapping("/workspace/actions/ensure") public JsonNode ensure(Authentication a) {return service.ensure(owner(a));}
    @GetMapping("/sessions")
    public JsonNode sessions(Authentication a,
            @RequestParam(defaultValue="0") @Min(0) int offset,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int limit,
            @RequestParam(defaultValue="") @Size(max=100) String query,
            @RequestParam(defaultValue="false") boolean archived) {
        return service.list(owner(a), offset, limit, query, archived);
    }
    @PostMapping("/sessions/{id}/actions/update")
    public JsonNode update(Authentication a, @PathVariable UUID id, @Valid @RequestBody UpdateDshSessionRequest r) {
        return service.post(owner(a), "/sessions/"+id+"/actions/update", r);
    }
    @PostMapping("/sessions/{id}/actions/archive")
    public JsonNode archive(Authentication a, @PathVariable UUID id) {
        return service.post(owner(a), "/sessions/"+id+"/actions/archive", Map.of());
    }
    @PostMapping("/sessions/{id}/actions/restore")
    public JsonNode restore(Authentication a, @PathVariable UUID id) {
        return service.post(owner(a), "/sessions/"+id+"/actions/restore", Map.of());
    }
    @PostMapping("/sessions") public org.springframework.http.ResponseEntity<JsonNode> create(Authentication a,@Valid @RequestBody CreateDshSessionRequest r) {return service.command(owner(a),"/sessions",r);}
    @GetMapping("/sessions/{id}") public JsonNode session(Authentication a,@PathVariable UUID id) {return service.get(owner(a),"/sessions/"+id);}
    @GetMapping("/sessions/{id}/messages")
    @Operation(summary="读取助手会话历史", description="只读当前用户所属会话，不启动模型。返回用户原文、工具内容及附件摘要。跨用户 404；游标和 offset 模式混用返回 400。归档历史仍可读取。")
    @ApiResponse(responseCode="200", description="历史消息及分页边界", content=@Content(schema=@Schema(implementation=DshMessagePageResponse.class)))
    public JsonNode messages(Authentication a, @Parameter(description="当前用户会话 UUID") @PathVariable UUID id,
            @Parameter(description="旧分页起始下标，默认 0；不能与 cursor 模式混用") @RequestParam(required=false) @Min(0) Integer offset,
            @Parameter(description="消息数量，默认 50，范围 1～200") @RequestParam(defaultValue="50") @Min(1) @Max(200) int limit,
            @Parameter(description="指定 cursor 使用向前游标分页；不传时使用 offset 模式") @RequestParam(required=false) String mode,
            @Parameter(description="cursor 模式读取此序号以前的消息，不传则读取最近消息；非负整数") @RequestParam(required=false) @Min(0) Long beforeSeq) {
        return service.messages(owner(a), id, offset, limit, mode, beforeSeq);
    }
    @PostMapping("/sessions/{id}/messages") @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary="发送助手消息", description="文字和已上传附件至少提供一种，最多引用当前会话 5 个附件。202 表示已持久化接收，模型随后执行。忙碌、归档或消息标识冲突返回 409；无权附件 404；模型明确不支持图片返回 422，消息不会入队。接收结果不确定时先读取历史，不能使用新标识直接重发。")
    @ApiResponse(responseCode="202", description="消息已接收或此前已接收", content=@Content(schema=@Schema(implementation=DshMessageReceiptResponse.class)))
    public JsonNode send(Authentication a,@Parameter(description="当前用户会话 UUID") @PathVariable UUID id,@Valid @RequestBody SendDshMessageRequest r) {return service.send(owner(a),id,r);}
    @PostMapping("/sessions/{id}/actions/resume") public JsonNode resume(Authentication a,@PathVariable UUID id) {return service.post(owner(a),"/sessions/"+id+"/actions/resume",Map.of());}
    @PostMapping("/sessions/{id}/actions/cancel") public org.springframework.http.ResponseEntity<JsonNode> cancel(Authentication a,@PathVariable UUID id) {return service.command(owner(a),"/sessions/"+id+"/actions/cancel",Map.of());}
    @PostMapping("/sessions/{id}/interactions/{interactionId}/actions/respond") public JsonNode respond(Authentication a,@PathVariable UUID id,@PathVariable UUID interactionId,@Valid @RequestBody RespondDshQuestionRequest r) {return service.post(owner(a),"/sessions/"+id+"/interactions/"+interactionId+"/actions/respond",r);}
    @GetMapping(value="/sessions/{id}/events",produces="text/event-stream") public SseEmitter events(Authentication a,@PathVariable UUID id) {return events.subscribe(identities.require(a),id);}
}

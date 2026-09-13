package cn.superhuang.data.scalpel.business.mcp.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.mcp.service.McpManagementService;
import cn.superhuang.data.scalpel.business.mcp.service.McpAccessTokenManagementService;
import cn.superhuang.data.scalpel.business.mcp.web.request.McpAccessTokenGrantRequest;
import cn.superhuang.data.scalpel.business.mcp.web.request.CreateMcpServerRequest;
import cn.superhuang.data.scalpel.business.mcp.web.request.UpdateMcpServerRequest;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpReleaseResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpAccessTokenResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpServerResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpTokenResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "MCP 平台服务")
@RestController
@RequestMapping("/api/v1/mcp-servers")
public class McpServerResource {
    private final McpManagementService service;
    private final McpAccessTokenManagementService accessTokens;
    public McpServerResource(McpManagementService service, McpAccessTokenManagementService accessTokens){this.service=service;this.accessTokens=accessTokens;}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台服务：查询列表")
    @Operation(summary = "查询 MCP Server", description = "按通用 Search DSL 分页查询在线开发平台的 MCP Server，返回草稿修订、生命周期、当前发布版本和 Tool 数量摘要；不会读取 Tool 脚本正文。")
    @GetMapping @PreAuthorize("hasAuthority('mcp.view')") public PageResponse<McpServerResponse> search(@ParameterObject @ModelAttribute SearchRequest request){return service.search(request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台服务：查看详情")
    @Operation(summary = "查看 MCP Server 详情", description = "返回 Server 基础信息、草稿修订、发布状态和当前活动 Release，不包含访问令牌明文。")
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('mcp.view')") public McpServerResponse get(@Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id){return service.get(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：创建")
    @Operation(summary = "创建 MCP Server", description = "创建一个 DRAFT Server 并初始化草稿修订；不会自动发布、启用或签发访问凭证。Server 编码作为公开 MCP 路径标识，创建后不可与其他 Server 重复。")
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('mcp.create')") public McpServerResponse create(@Valid @RequestBody CreateMcpServerRequest request){return service.create(request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：修改")
    @Operation(summary = "修改 MCP Server", description = "整体修改 Server 名称、目录、用途说明和智能体 instructions；只有规范化后的内容实际变化时才增加草稿修订。不会改变当前已发布 Release，调用端继续使用原活动版本。")
    @PostMapping("/{id}/actions/update") @PreAuthorize("hasAuthority('mcp.update')") public McpServerResponse update(@Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id,@Valid @RequestBody UpdateMcpServerRequest request){return service.update(id,request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "MCP 平台服务：发布")
    @Operation(summary = "发布 MCP Server", description = "要求至少一个启用 Tool，校验其输入/输出 Schema 和 Groovy 编译结果，创建不可变 Release；若定义摘要与最新 Release 相同则复用该版本。随后原子切换活动版本并进入 ENABLED，任一 Tool 无效则不发布。")
    @PostMapping("/{id}/actions/publish") @PreAuthorize("hasAuthority('mcp.publish')") public McpServerResponse publish(@Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id){return service.publish(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：停用")
    @Operation(summary = "停用 MCP Server", description = "阻止后续公开 MCP 调用，同时保留最后活动 Release 和草稿；已授权访问令牌本身不被停用。")
    @PostMapping("/{id}/actions/disable") @PreAuthorize("hasAuthority('mcp.publish')") public McpServerResponse disable(@Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id){return service.disable(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：启用")
    @Operation(summary = "重新启用 MCP Server", description = "恢复最后一次已发布 Release 的公开调用，不发布当前草稿；从未发布的 Server 不能启用。")
    @PostMapping("/{id}/actions/enable") @PreAuthorize("hasAuthority('mcp.publish')") public McpServerResponse enable(@Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id){return service.enable(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：删除")
    @Operation(summary = "删除 MCP Server", description = "永久删除从未发布的 DRAFT Server 及其 Tool 草稿。存在任何发布版本的 Server 不能物理删除。")
    @PostMapping("/{id}/actions/delete") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('mcp.delete')") public void delete(@Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id){service.delete(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台服务：读取兼容访问凭证")
    @Operation(summary = "读取旧版 Server 凭证", description = "已停用的兼容接口，固定返回 HTTP 410，提示改用独立 MCP 访问凭证；不会返回任何历史 Server Token。")
    @GetMapping("/{id}/token") @PreAuthorize("hasAuthority('mcp.token.manage')") public McpTokenResponse token(@Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id){return service.token(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：轮换凭证")
    @Operation(summary = "轮换旧版 Server 凭证", description = "已停用的兼容接口，固定返回 HTTP 410，提示改用独立 MCP 访问凭证的轮换接口。")
    @PostMapping("/{id}/actions/rotate-token") @PreAuthorize("hasAuthority('mcp.token.manage')") public McpTokenResponse rotate(@Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id){return service.rotateToken(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台服务：查询授权令牌")
    @Operation(summary = "查询 Server 已授权访问凭证", description = "分页查询当前获准调用该 Server 的独立访问凭证及状态、有效期和脱敏提示；不返回 Token 明文或摘要。")
    @GetMapping("/{id}/access-tokens") @PreAuthorize("hasAuthority('mcp.token.manage')") public PageResponse<McpAccessTokenResponse> accessTokens(
            @Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id, @ParameterObject @ModelAttribute SearchRequest request){return accessTokens.searchForServer(id,request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：授予令牌访问权")
    @Operation(summary = "授权访问凭证调用 Server", description = "建立独立访问凭证与 Server 的授权关系。授权覆盖该 Server 当前及后续活动 Release 的全部 Tool，并在后续请求中立即生效。")
    @PostMapping("/{id}/actions/grant-access-token") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('mcp.token.manage')") public void grantAccessToken(
            @Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id,@Valid @RequestBody McpAccessTokenGrantRequest request){accessTokens.grant(id,request.accessTokenId());}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：撤回令牌访问权")
    @Operation(summary = "撤回访问凭证的 Server 权限", description = "删除访问凭证与 Server 的授权关系，后续使用该 Token 调用此 Server 将立即返回 403；不影响它对其他 Server 的授权。")
    @PostMapping("/{id}/actions/revoke-access-token") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('mcp.token.manage')") public void revokeAccessToken(
            @Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id,@Valid @RequestBody McpAccessTokenGrantRequest request){accessTokens.revoke(id,request.accessTokenId());}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台服务：查询发布版本")
    @Operation(summary = "查询 MCP Server 发布版本", description = "按版本倒序返回 Server 的不可变 Release 列表、摘要、发布时间和 Tool 快照概要；历史版本只读且不能回滚为活动版本。")
    @GetMapping("/{id}/releases") @PreAuthorize("hasAuthority('mcp.view')") public List<McpReleaseResponse> releases(@Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id){return service.releases(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台服务：查询发布详情")
    @Operation(summary = "查看 MCP Server 发布详情", description = "读取指定版本的不可变 Server 与 Tool 定义快照，用于审计发布内容；不会执行 Tool 或改变活动版本。")
    @GetMapping("/{id}/releases/{version}") @PreAuthorize("hasAuthority('mcp.view')") public McpReleaseResponse release(@Parameter(description = "MCP 服务 UUID。") @PathVariable UUID id,@Parameter(description = "资源或定义版本号。") @PathVariable int version){return service.release(id,version);}
}

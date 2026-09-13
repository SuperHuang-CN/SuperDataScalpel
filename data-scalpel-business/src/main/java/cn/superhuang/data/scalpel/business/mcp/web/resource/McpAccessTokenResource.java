package cn.superhuang.data.scalpel.business.mcp.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.mcp.service.McpAccessTokenManagementService;
import cn.superhuang.data.scalpel.business.mcp.web.request.CreateMcpAccessTokenRequest;
import cn.superhuang.data.scalpel.business.mcp.web.request.UpdateMcpAccessTokenRequest;
import cn.superhuang.data.scalpel.business.mcp.web.request.UpdateMcpAccessTokenServersRequest;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpAccessTokenDetailResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpAccessTokenIssuedResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpAccessTokenResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpAccessTokenSecretResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpAuthorizedServerResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "MCP 平台令牌")
@RestController
@RequestMapping("/api/v1/mcp-access-tokens")
@PreAuthorize("hasAuthority('mcp.token.manage')")
public class McpAccessTokenResource {
    private final McpAccessTokenManagementService service;

    public McpAccessTokenResource(McpAccessTokenManagementService service) { this.service = service; }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台令牌：查询列表")
    @Operation(summary = "查询 MCP 访问凭证", description = "按通用 Search DSL 分页查询独立 MCP Token 的名称、状态、有效期、脱敏提示、轮换版本和最近调用时间；不返回完整 Token 或认证摘要。")
    @GetMapping
    public PageResponse<McpAccessTokenResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台令牌：查询候选服务")
    @Operation(summary = "查询 MCP Server 授权候选", description = "按通用 Search DSL 分页查询全部现有 MCP Server，包含 DRAFT、ENABLED 和 DISABLED 状态；结果本身不表示某个令牌已经授权。使用令牌管理权限，不要求普通 Server 查看权限。")
    @GetMapping("/server-candidates")
    public PageResponse<McpAuthorizedServerResponse> serverCandidates(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.searchServerCandidates(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台令牌：查看详情")
    @Operation(summary = "查看 MCP 访问凭证详情", description = "返回凭证元数据和已授权 Server 列表；完整 Token 需要单独调用 secret 接口读取。")
    @GetMapping("/{id}")
    public McpAccessTokenDetailResponse get(@Parameter(description = "MCP 访问令牌 UUID。") @PathVariable UUID id) { return service.get(id); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：创建")
    @Operation(summary = "创建 MCP 访问凭证", description = "生成 dsmcp_ 前缀的随机 Token，保存 SHA-256 摘要和 AES-GCM 密文，并原子建立请求中的 Server 授权。响应禁止缓存并包含完整 Token。")
    @PostMapping
    public ResponseEntity<McpAccessTokenIssuedResponse> create(@Valid @RequestBody CreateMcpAccessTokenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.create(request));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：修改")
    @Operation(summary = "修改 MCP 访问凭证", description = "修改凭证名称和过期时间，不改变 Token 字符串、状态、轮换版本或 Server 授权。过期时间在后续认证请求中立即生效。")
    @PostMapping("/{id}/actions/update")
    public McpAccessTokenResponse update(@Parameter(description = "MCP 访问令牌 UUID。") @PathVariable UUID id, @Valid @RequestBody UpdateMcpAccessTokenRequest request) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：修改服务授权")
    @Operation(summary = "替换 MCP 访问凭证授权", description = "用请求中的完整 Server ID 集合原子替换该凭证的授权关系；缺失的旧授权会被撤回，新增授权立即生效。")
    @PostMapping("/{id}/actions/update-servers")
    public McpAccessTokenDetailResponse updateServers(@Parameter(description = "MCP 访问令牌 UUID。") @PathVariable UUID id,
                                                       @Valid @RequestBody UpdateMcpAccessTokenServersRequest request) {
        return service.updateServers(id, request.serverIds());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：启用")
    @Operation(summary = "启用 MCP 访问凭证", description = "允许未过期凭证用于其已授权 Server 的后续 MCP 请求；不会改变授权集合或轮换版本。")
    @PostMapping("/{id}/actions/enable")
    public McpAccessTokenResponse enable(@Parameter(description = "MCP 访问令牌 UUID。") @PathVariable UUID id) { return service.enable(id); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：停用")
    @Operation(summary = "停用 MCP 访问凭证", description = "使该 Token 的后续 MCP 请求立即认证失败，同时保留凭证、授权和调用历史以便再次启用或审计。")
    @PostMapping("/{id}/actions/disable")
    public McpAccessTokenResponse disable(@Parameter(description = "MCP 访问令牌 UUID。") @PathVariable UUID id) { return service.disable(id); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：轮换凭证")
    @Operation(summary = "轮换 MCP 访问凭证", description = "生成并保存新的摘要和加密 Token、递增轮换版本，使旧 Token 立即失效；响应禁止缓存并返回新的完整 Token。")
    @PostMapping("/{id}/actions/rotate")
    public ResponseEntity<McpAccessTokenIssuedResponse> rotate(@Parameter(description = "MCP 访问令牌 UUID。") @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.rotate(id));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台令牌：读取访问凭证")
    @Operation(summary = "读取完整 MCP Token", description = "使用令牌管理权限按需解密并返回当前完整 Token，响应禁止缓存。凭据加密密钥不可用时返回 503；该操作不轮换 Token，也不改变最近使用时间。")
    @GetMapping("/{id}/secret")
    public ResponseEntity<McpAccessTokenSecretResponse> secret(@Parameter(description = "MCP 访问令牌 UUID。") @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.secret(id));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：删除")
    @Operation(summary = "删除 MCP 访问凭证", description = "永久删除凭证及全部 Server 授权，后续认证立即失效；历史调用日志保留脱敏快照且允许凭证引用为空。")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@Parameter(description = "MCP 访问令牌 UUID。") @PathVariable UUID id) { service.delete(id); }
}

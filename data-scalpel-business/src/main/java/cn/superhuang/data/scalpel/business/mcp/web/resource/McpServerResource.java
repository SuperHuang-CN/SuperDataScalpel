package cn.superhuang.data.scalpel.business.mcp.web.resource;

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
    @GetMapping @PreAuthorize("hasAuthority('mcp.view')") public PageResponse<McpServerResponse> search(@ParameterObject @ModelAttribute SearchRequest request){return service.search(request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台服务：查看详情")
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('mcp.view')") public McpServerResponse get(@PathVariable UUID id){return service.get(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：创建")
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('mcp.create')") public McpServerResponse create(@Valid @RequestBody CreateMcpServerRequest request){return service.create(request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：修改")
    @PostMapping("/{id}/actions/update") @PreAuthorize("hasAuthority('mcp.update')") public McpServerResponse update(@PathVariable UUID id,@Valid @RequestBody UpdateMcpServerRequest request){return service.update(id,request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "MCP 平台服务：发布")
    @PostMapping("/{id}/actions/publish") @PreAuthorize("hasAuthority('mcp.publish')") public McpServerResponse publish(@PathVariable UUID id){return service.publish(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：停用")
    @PostMapping("/{id}/actions/disable") @PreAuthorize("hasAuthority('mcp.publish')") public McpServerResponse disable(@PathVariable UUID id){return service.disable(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：启用")
    @PostMapping("/{id}/actions/enable") @PreAuthorize("hasAuthority('mcp.publish')") public McpServerResponse enable(@PathVariable UUID id){return service.enable(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：删除")
    @PostMapping("/{id}/actions/delete") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('mcp.delete')") public void delete(@PathVariable UUID id){service.delete(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台服务：读取兼容访问凭证")
    @GetMapping("/{id}/token") @PreAuthorize("hasAuthority('mcp.token.manage')") public McpTokenResponse token(@PathVariable UUID id){return service.token(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：轮换凭证")
    @PostMapping("/{id}/actions/rotate-token") @PreAuthorize("hasAuthority('mcp.token.manage')") public McpTokenResponse rotate(@PathVariable UUID id){return service.rotateToken(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台服务：查询授权令牌")
    @GetMapping("/{id}/access-tokens") @PreAuthorize("hasAuthority('mcp.token.manage')") public PageResponse<McpAccessTokenResponse> accessTokens(
            @PathVariable UUID id, @ParameterObject @ModelAttribute SearchRequest request){return accessTokens.searchForServer(id,request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：授予令牌访问权")
    @PostMapping("/{id}/actions/grant-access-token") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('mcp.token.manage')") public void grantAccessToken(
            @PathVariable UUID id,@Valid @RequestBody McpAccessTokenGrantRequest request){accessTokens.grant(id,request.accessTokenId());}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台服务：撤回令牌访问权")
    @PostMapping("/{id}/actions/revoke-access-token") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('mcp.token.manage')") public void revokeAccessToken(
            @PathVariable UUID id,@Valid @RequestBody McpAccessTokenGrantRequest request){accessTokens.revoke(id,request.accessTokenId());}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台服务：查询发布版本")
    @GetMapping("/{id}/releases") @PreAuthorize("hasAuthority('mcp.view')") public List<McpReleaseResponse> releases(@PathVariable UUID id){return service.releases(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台服务：查询发布详情")
    @GetMapping("/{id}/releases/{version}") @PreAuthorize("hasAuthority('mcp.view')") public McpReleaseResponse release(@PathVariable UUID id,@PathVariable int version){return service.release(id,version);}
}

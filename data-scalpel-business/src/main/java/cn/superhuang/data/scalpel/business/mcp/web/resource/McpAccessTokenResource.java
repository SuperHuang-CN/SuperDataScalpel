package cn.superhuang.data.scalpel.business.mcp.web.resource;

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
    @GetMapping
    public PageResponse<McpAccessTokenResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台令牌：查询候选服务")
    @GetMapping("/server-candidates")
    public PageResponse<McpAuthorizedServerResponse> serverCandidates(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.searchServerCandidates(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台令牌：查看详情")
    @GetMapping("/{id}")
    public McpAccessTokenDetailResponse get(@PathVariable UUID id) { return service.get(id); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：创建")
    @PostMapping
    public ResponseEntity<McpAccessTokenIssuedResponse> create(@Valid @RequestBody CreateMcpAccessTokenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(service.create(request));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：修改")
    @PostMapping("/{id}/actions/update")
    public McpAccessTokenResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateMcpAccessTokenRequest request) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：修改服务授权")
    @PostMapping("/{id}/actions/update-servers")
    public McpAccessTokenDetailResponse updateServers(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateMcpAccessTokenServersRequest request) {
        return service.updateServers(id, request.serverIds());
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：启用")
    @PostMapping("/{id}/actions/enable")
    public McpAccessTokenResponse enable(@PathVariable UUID id) { return service.enable(id); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：停用")
    @PostMapping("/{id}/actions/disable")
    public McpAccessTokenResponse disable(@PathVariable UUID id) { return service.disable(id); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：轮换凭证")
    @PostMapping("/{id}/actions/rotate")
    public ResponseEntity<McpAccessTokenIssuedResponse> rotate(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.rotate(id));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台令牌：读取访问凭证")
    @GetMapping("/{id}/secret")
    public ResponseEntity<McpAccessTokenSecretResponse> secret(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.secret(id));
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台令牌：删除")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) { service.delete(id); }
}

package cn.superhuang.data.scalpel.business.mcp.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.mcp.service.McpManagementService;
import cn.superhuang.data.scalpel.business.mcp.web.request.ExecuteMcpToolDraftRequest;
import cn.superhuang.data.scalpel.business.mcp.web.request.SaveMcpToolRequest;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpDraftExecutionResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpToolResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "MCP 平台工具")
@RestController
@RequestMapping("/api/v1/mcp-servers/{serverId}/tools")
public class McpToolResource {
    private final McpManagementService service;
    public McpToolResource(McpManagementService service){this.service=service;}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台工具：查询列表")
    @GetMapping @PreAuthorize("hasAuthority('mcp.view')") public List<McpToolResponse> list(@PathVariable UUID serverId){return service.tools(serverId);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台工具：查看详情")
    @GetMapping("/{toolId}") @PreAuthorize("hasAuthority('mcp.view')") public McpToolResponse get(@PathVariable UUID serverId,@PathVariable UUID toolId){return service.tool(serverId,toolId);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台工具：创建")
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('mcp.update')") public McpToolResponse create(@PathVariable UUID serverId,@Valid @RequestBody SaveMcpToolRequest request){return service.createTool(serverId,request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台工具：修改")
    @PostMapping("/{toolId}/actions/update") @PreAuthorize("hasAuthority('mcp.update')") public McpToolResponse update(@PathVariable UUID serverId,@PathVariable UUID toolId,@Valid @RequestBody SaveMcpToolRequest request){return service.updateTool(serverId,toolId,request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台工具：删除")
    @PostMapping("/{toolId}/actions/delete") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('mcp.delete')") public void delete(@PathVariable UUID serverId,@PathVariable UUID toolId){service.deleteTool(serverId,toolId);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "MCP 平台工具：执行工具")
    @PostMapping("/actions/execute-draft") @PreAuthorize("hasAuthority('mcp.execute')") public McpDraftExecutionResponse execute(@PathVariable UUID serverId,@Valid @RequestBody ExecuteMcpToolDraftRequest request){return service.executeDraft(serverId,request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台工具：查询工具摘要")
    @GetMapping("/summaries") @PreAuthorize("hasAuthority('mcp.view')")
    public List<cn.superhuang.data.scalpel.business.mcp.web.response.McpToolSummaryResponse> summaries(@PathVariable UUID serverId){
        return service.toolSummaries(serverId);
    }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台工具：获取代码补全")
    @GetMapping("/script-completion") @PreAuthorize("hasAuthority('mcp.view')") public Map<String,Object> completion(@PathVariable UUID serverId){service.get(serverId);return Map.of("bindings",List.of("args","context","log","json"),"language","groovy");}
}

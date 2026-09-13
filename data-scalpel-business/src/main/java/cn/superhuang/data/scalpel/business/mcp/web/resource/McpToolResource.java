package cn.superhuang.data.scalpel.business.mcp.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
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
    @Operation(summary = "查询 MCP Tool 完整列表", description = "返回 Server 的全部 Tool 草稿，包含 Schema、Groovy 脚本和样例大文本；用于兼容编辑页面，普通列表优先使用 summaries 接口。")
    @GetMapping @PreAuthorize("hasAuthority('mcp.view')") public List<McpToolResponse> list(@Parameter(description = "MCP 服务器 UUID。") @PathVariable UUID serverId){return service.tools(serverId);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台工具：查看详情")
    @Operation(summary = "查看 MCP Tool 草稿", description = "返回指定 Tool 的名称、说明、启用状态、输入/输出 Schema、Groovy 脚本、样例和草稿修订；内容可能与当前发布版本不同。")
    @GetMapping("/{toolId}") @PreAuthorize("hasAuthority('mcp.view')") public McpToolResponse get(@Parameter(description = "MCP 服务器 UUID。") @PathVariable UUID serverId,@Parameter(description = "MCP 工具 UUID。") @PathVariable UUID toolId){return service.tool(serverId,toolId);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台工具：创建")
    @Operation(summary = "创建 MCP Tool 草稿", description = "在指定 Server 中创建 Tool，校验根类型为 object 的本地 JSON Schema、脚本大小、样例和 Groovy 编译结果；只更新草稿，不影响当前 Release。")
    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('mcp.update')") public McpToolResponse create(@Parameter(description = "MCP 服务器 UUID。") @PathVariable UUID serverId,@Valid @RequestBody SaveMcpToolRequest request){return service.createTool(serverId,request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台工具：修改")
    @Operation(summary = "修改 MCP Tool 草稿", description = "在有界执行器中校验 Schema 并编译 Groovy 后保存草稿和增加修订；提供 expectedRevision 时会拒绝覆盖并发修改。当前发布 Tool 不受影响。")
    @PostMapping("/{toolId}/actions/update") @PreAuthorize("hasAuthority('mcp.update')") public McpToolResponse update(@Parameter(description = "MCP 服务器 UUID。") @PathVariable UUID serverId,@Parameter(description = "MCP 工具 UUID。") @PathVariable UUID toolId,@Valid @RequestBody SaveMcpToolRequest request){return service.updateTool(serverId,toolId,request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "MCP 平台工具：删除")
    @Operation(summary = "删除 MCP Tool 草稿", description = "从 Server 草稿中永久删除 Tool；已经发布的 Release 快照保持不可变，调用端在再次发布前仍可调用旧版本 Tool。")
    @PostMapping("/{toolId}/actions/delete") @ResponseStatus(HttpStatus.NO_CONTENT) @PreAuthorize("hasAuthority('mcp.delete')") public void delete(@Parameter(description = "MCP 服务器 UUID。") @PathVariable UUID serverId,@Parameter(description = "MCP 工具 UUID。") @PathVariable UUID toolId){service.deleteTool(serverId,toolId);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "MCP 平台工具：执行工具")
    @Operation(summary = "执行 MCP Tool 草稿", description = "使用请求中提交的临时 Schema、Groovy 脚本和参数执行草稿，serverId 只提供存在性校验与脚本上下文。返回结构化结果和最多 100 行调试日志；不会读取或保存现有 Tool 草稿，也不写 MCP 调用日志。Groovy 是受信任代码，不是沙箱。")
    @PostMapping("/actions/execute-draft") @PreAuthorize("hasAuthority('mcp.execute')") public McpDraftExecutionResponse execute(@Parameter(description = "MCP 服务器 UUID。") @PathVariable UUID serverId,@Valid @RequestBody ExecuteMcpToolDraftRequest request){return service.executeDraft(serverId,request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台工具：查询工具摘要")
    @Operation(summary = "查询 MCP Tool 摘要", description = "返回 Server 的 Tool 标识、名称、启用状态和修订摘要，不读取或返回脚本、Schema 和样例大文本，适合列表页面。")
    @GetMapping("/summaries") @PreAuthorize("hasAuthority('mcp.view')")
    public List<cn.superhuang.data.scalpel.business.mcp.web.response.McpToolSummaryResponse> summaries(@Parameter(description = "MCP 服务器 UUID。") @PathVariable UUID serverId){
        return service.toolSummaries(serverId);
    }
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台工具：获取代码补全")
    @Operation(summary = "获取 MCP Groovy 绑定信息", description = "确认 Server 存在后返回脚本编辑器可用的 args、context、log、json 绑定和 Groovy 语言标识；不执行或编译脚本。")
    @GetMapping("/script-completion") @PreAuthorize("hasAuthority('mcp.view')") public Map<String,Object> completion(@Parameter(description = "MCP 服务器 UUID。") @PathVariable UUID serverId){service.get(serverId);return Map.of("bindings",List.of("args","context","log","json"),"language","groovy");}
}

package cn.superhuang.data.scalpel.business.mcp.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.mcp.service.McpInvocationLogService;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpInvocationOverviewResponse;
import cn.superhuang.data.scalpel.business.mcp.web.response.McpInvocationResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "MCP 平台调用记录")
@RestController @RequestMapping("/api/v1/mcp-invocations") @PreAuthorize("hasAuthority('mcp.view')")
public class McpInvocationResource {
    private final McpInvocationLogService service; public McpInvocationResource(McpInvocationLogService service){this.service=service;}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台调用记录：查询列表")
    @Operation(summary = "查询 MCP 调用记录", description = "按通用 Search DSL 分页查询协议方法和 Tool 调用的 Server、Release、状态、耗时、字节数、来源及凭证快照；不记录 Tool 参数、结果或脚本日志正文。")
    @GetMapping public PageResponse<McpInvocationResponse> search(@ParameterObject @ModelAttribute SearchRequest request){return service.search(request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台调用记录：查看详情")
    @Operation(summary = "查看 MCP 调用记录详情", description = "返回单次 MCP 请求的协议版本、请求 ID、Server/Release/Tool 快照、认证凭证提示、状态和稳定错误分类；不包含业务正文。")
    @GetMapping("/{id}") public McpInvocationResponse get(@Parameter(description = "MCP 调用记录 UUID。") @PathVariable UUID id){return service.get(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台调用记录：查询概览")
    @Operation(summary = "查询 MCP 调用统计概览", description = "汇总保留期内调用总数、成功、错误、拒绝和主要耗时指标，用于平台运行概览；统计基于已保存元数据。")
    @GetMapping("/statistics/overview") public McpInvocationOverviewResponse overview(){return service.overview();}
}

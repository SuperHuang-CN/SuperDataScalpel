package cn.superhuang.data.scalpel.business.mcp.web.resource;

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
    @GetMapping public PageResponse<McpInvocationResponse> search(@ParameterObject @ModelAttribute SearchRequest request){return service.search(request);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台调用记录：查看详情")
    @GetMapping("/{id}") public McpInvocationResponse get(@PathVariable UUID id){return service.get(id);}
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台调用记录：查询概览")
    @GetMapping("/statistics/overview") public McpInvocationOverviewResponse overview(){return service.overview();}
}

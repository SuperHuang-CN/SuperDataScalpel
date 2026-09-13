package cn.superhuang.data.scalpel.business.mcp.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@io.swagger.v3.oas.annotations.tags.Tag(name = "MCP 平台代码补全")
@RestController
@RequestMapping("/api/v1/mcp-tools")
public class McpToolCompletionResource {
    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "MCP 平台代码补全：获取代码补全")
    @Operation(summary = "获取 MCP Groovy 代码补全", description = "返回在线编辑器可用的 Groovy 语言、args/context/log/json 绑定和内置代码片段；不访问具体 Server，也不执行脚本。")
    @GetMapping("/script-completion")
    @PreAuthorize("hasAuthority('mcp.view')")
    public Map<String, Object> completion() {
        return Map.of(
                "language", "groovy",
                "bindings", List.of("args", "context", "log", "json"),
                "snippets", List.of(
                        Map.of("label", "return map", "insertText", "return [value: args.value]"),
                        Map.of("label", "log info", "insertText", "log.info('message')")
                )
        );
    }
}

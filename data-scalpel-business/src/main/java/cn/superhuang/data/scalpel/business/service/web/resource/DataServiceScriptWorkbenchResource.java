package cn.superhuang.data.scalpel.business.service.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.DataServiceScriptWorkbenchService;
import cn.superhuang.data.scalpel.business.service.web.request.ExecuteScriptDraftRequest;
import cn.superhuang.data.scalpel.contract.service.ScriptCompletionResponse;
import cn.superhuang.data.scalpel.contract.service.ScriptDraftExecutionResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "数据服务脚本工作台")
@RestController
@RequestMapping("/api/v1/data-services")
public class DataServiceScriptWorkbenchResource {

    private final DataServiceScriptWorkbenchService service;

    public DataServiceScriptWorkbenchResource(DataServiceScriptWorkbenchService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "数据服务脚本工作台：执行脚本草稿")
    @Operation(summary = "执行数据服务脚本草稿", description = "把未保存的 Groovy 脚本、模拟请求和路由发送到所选 DataScalpel Service Engine 实际执行，返回状态、响应、日志、SQL 跟踪和事务结果；不会保存 API Studio 配置或注册公开路由。脚本可按数据库账号权限执行读写 SQL，成功时提交事务、失败时回滚，因此该操作可能产生外部数据副作用；当前运行时不是 JVM 安全沙箱。数据源必须已启用并在目标 Engine 同步就绪。脚本自身的可确认失败通常仍返回 HTTP 200，以 status=FAILED 和 error 表达；Engine 调用失败返回网关错误。")
    @PostMapping("/actions/execute-script-draft")
    @PreAuthorize("hasAnyAuthority('service.create', 'service.update')")
    public ScriptDraftExecutionResponse executeDraft(@Valid @RequestBody ExecuteScriptDraftRequest request) {
        return service.executeDraft(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务脚本工作台：获取代码补全")
    @Operation(summary = "获取数据服务脚本补全", description = "从所选 DataScalpel Service Engine 读取脚本 API、变量、语法以及已同步数据源的表和字段元数据，不执行脚本也不修改远端配置，但会访问 Engine 和数据源元数据。Engine 和数据源必须已启用，数据源登记必须为 READY。")
    @GetMapping("/script-completion")
    @PreAuthorize("hasAnyAuthority('service.create', 'service.update')")
    public ScriptCompletionResponse completion(
            @Parameter(description = "DataScalpel Service Engine UUID。") @RequestParam UUID engineId,
            @Parameter(description = "数据源 UUID。") @RequestParam UUID dataSourceId
    ) {
        return service.completion(engineId, dataSourceId);
    }
}

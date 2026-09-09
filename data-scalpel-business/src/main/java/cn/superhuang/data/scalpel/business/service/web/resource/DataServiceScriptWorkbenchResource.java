package cn.superhuang.data.scalpel.business.service.web.resource;

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
    @PostMapping("/actions/execute-script-draft")
    @PreAuthorize("hasAnyAuthority('service.create', 'service.update')")
    public ScriptDraftExecutionResponse executeDraft(@Valid @RequestBody ExecuteScriptDraftRequest request) {
        return service.executeDraft(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "数据服务脚本工作台：获取代码补全")
    @GetMapping("/script-completion")
    @PreAuthorize("hasAnyAuthority('service.create', 'service.update')")
    public ScriptCompletionResponse completion(
            @RequestParam UUID engineId,
            @RequestParam UUID dataSourceId
    ) {
        return service.completion(engineId, dataSourceId);
    }
}

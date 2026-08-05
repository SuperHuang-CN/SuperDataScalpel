package cn.superhuang.data.scalpel.business.service.web.resource;

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

@RestController
@RequestMapping("/api/v1/data-services")
public class DataServiceScriptWorkbenchResource {

    private final DataServiceScriptWorkbenchService service;

    public DataServiceScriptWorkbenchResource(DataServiceScriptWorkbenchService service) {
        this.service = service;
    }

    @PostMapping("/actions/execute-script-draft")
    @PreAuthorize("hasAnyAuthority('service.create', 'service.update')")
    public ScriptDraftExecutionResponse executeDraft(@Valid @RequestBody ExecuteScriptDraftRequest request) {
        return service.executeDraft(request);
    }

    @GetMapping("/script-completion")
    @PreAuthorize("hasAnyAuthority('service.create', 'service.update')")
    public ScriptCompletionResponse completion(
            @RequestParam UUID engineId,
            @RequestParam UUID dataSourceId
    ) {
        return service.completion(engineId, dataSourceId);
    }
}

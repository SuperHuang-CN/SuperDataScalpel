package cn.superhuang.data.scalpel.engine.script.web.resource;

import cn.superhuang.data.scalpel.contract.service.ScriptCompletionResponse;
import cn.superhuang.data.scalpel.contract.service.ScriptDraftExecutionRequest;
import cn.superhuang.data.scalpel.contract.service.ScriptDraftExecutionResponse;
import cn.superhuang.data.scalpel.engine.script.EnginePublishedScriptService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/scripts")
public class EngineScriptWorkbenchResource {

    private final EnginePublishedScriptService service;

    public EngineScriptWorkbenchResource(EnginePublishedScriptService service) {
        this.service = service;
    }

    @PostMapping("/actions/execute-draft")
    public ScriptDraftExecutionResponse executeDraft(@Valid @RequestBody ScriptDraftExecutionRequest request) {
        return service.executeDraft(request);
    }

    @GetMapping("/completion")
    public ScriptCompletionResponse completion(@RequestParam UUID dataSourceId) {
        return service.completion(dataSourceId);
    }
}

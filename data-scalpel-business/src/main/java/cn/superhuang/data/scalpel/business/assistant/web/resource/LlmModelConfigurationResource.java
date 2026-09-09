package cn.superhuang.data.scalpel.business.assistant.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.assistant.service.LlmModelManagementService;
import cn.superhuang.data.scalpel.business.assistant.web.request.CreateLlmModelRequest;
import cn.superhuang.data.scalpel.business.assistant.web.request.UpdateLlmModelRequest;
import cn.superhuang.data.scalpel.business.assistant.web.response.LlmModelConfigurationResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/v1/system/llm-models")
@Tag(name = "系统管理-AI 模型")
public class LlmModelConfigurationResource {

    private final LlmModelManagementService service;

    public LlmModelConfigurationResource(LlmModelManagementService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 AI 模型配置")
    @GetMapping
    @PreAuthorize("hasAuthority('system.configuration.view')")
    @Operation(summary = "查询 AI 模型配置")
    public PageResponse<LlmModelConfigurationResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "系统管理-AI 模型：查看详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('system.configuration.view')")
    public LlmModelConfigurationResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "系统管理-AI 模型：创建")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system.configuration.update')")
    public LlmModelConfigurationResponse create(@Valid @RequestBody CreateLlmModelRequest request) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "系统管理-AI 模型：修改")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    public LlmModelConfigurationResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLlmModelRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "系统管理-AI 模型：测试连接")
    @PostMapping("/{id}/actions/test")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    public LlmModelConfigurationResponse test(@PathVariable UUID id) { return service.test(id); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "系统管理-AI 模型：启用")
    @PostMapping("/{id}/actions/enable")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    public LlmModelConfigurationResponse enable(@PathVariable UUID id) { return service.enable(id); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "系统管理-AI 模型：停用")
    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    public LlmModelConfigurationResponse disable(@PathVariable UUID id) { return service.disable(id); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "系统管理-AI 模型：设为默认模型")
    @PostMapping("/{id}/actions/set-default")
    @PreAuthorize("hasAuthority('system.configuration.update')")
    public LlmModelConfigurationResponse setDefault(@PathVariable UUID id) { return service.setDefault(id); }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "系统管理-AI 模型：删除")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('system.configuration.update')")
    public void delete(@PathVariable UUID id) { service.delete(id); }
}

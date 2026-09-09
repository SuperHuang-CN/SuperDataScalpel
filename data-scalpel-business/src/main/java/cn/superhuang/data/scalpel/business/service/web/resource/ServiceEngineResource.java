package cn.superhuang.data.scalpel.business.service.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.ServiceEngineManagementService;
import cn.superhuang.data.scalpel.business.service.web.request.CreateServiceEngineRequest;
import cn.superhuang.data.scalpel.business.service.web.request.TestServiceEngineRequest;
import cn.superhuang.data.scalpel.business.service.web.request.TestStoredServiceEngineRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateServiceEngineRequest;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineResponse;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineTestResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
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

@io.swagger.v3.oas.annotations.tags.Tag(name = "服务引擎")
@RestController
@RequestMapping("/api/v1/service-engines")
public class ServiceEngineResource {

    private final ServiceEngineManagementService service;

    public ServiceEngineResource(ServiceEngineManagementService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "服务引擎：查询列表")
    @GetMapping
    @PreAuthorize("hasAuthority('service.engine.view')")
    public PageResponse<ServiceEngineResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "服务引擎：查看详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.engine.view')")
    public ServiceEngineResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "服务引擎：创建")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.engine.create')")
    public ServiceEngineResponse create(@Valid @RequestBody CreateServiceEngineRequest request) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "服务引擎：修改")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('service.engine.update')")
    public ServiceEngineResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateServiceEngineRequest request) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "服务引擎：测试连接")
    @PostMapping("/{id}/actions/test")
    @PreAuthorize("hasAuthority('service.engine.test')")
    public ServiceEngineTestResponse test(
            @PathVariable UUID id,
            @Valid @RequestBody(required = false) TestStoredServiceEngineRequest request
    ) {
        return service.test(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "服务引擎：测试连接")
    @PostMapping("/actions/test")
    @PreAuthorize("hasAuthority('service.engine.test')")
    public ServiceEngineTestResponse test(@Valid @RequestBody TestServiceEngineRequest request) {
        return service.test(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "服务引擎：删除")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.engine.delete')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}

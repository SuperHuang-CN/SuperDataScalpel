package cn.superhuang.data.scalpel.business.service.web.resource;

import cn.superhuang.data.scalpel.business.service.ServiceEngineManagementService;
import cn.superhuang.data.scalpel.business.service.web.request.CreateServiceEngineRequest;
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

@RestController
@RequestMapping("/api/v1/service-engines")
public class ServiceEngineResource {

    private final ServiceEngineManagementService service;

    public ServiceEngineResource(ServiceEngineManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('service.engine.view')")
    public PageResponse<ServiceEngineResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.engine.view')")
    public ServiceEngineResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.engine.create')")
    public ServiceEngineResponse create(@Valid @RequestBody CreateServiceEngineRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('service.engine.update')")
    public ServiceEngineResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateServiceEngineRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/test")
    @PreAuthorize("hasAuthority('service.engine.test')")
    public ServiceEngineTestResponse test(@PathVariable UUID id) {
        return service.test(id);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.engine.delete')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}

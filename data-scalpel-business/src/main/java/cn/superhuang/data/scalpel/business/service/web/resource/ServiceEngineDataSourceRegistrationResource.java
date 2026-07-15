package cn.superhuang.data.scalpel.business.service.web.resource;

import cn.superhuang.data.scalpel.business.service.ServiceEngineDataSourceRegistrationService;
import cn.superhuang.data.scalpel.business.service.web.request.CreateServiceEngineDataSourceRegistrationRequest;
import cn.superhuang.data.scalpel.business.service.web.request.ServiceEngineDataSourceActionRequest;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineDataSourceRegistrationResponse;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineDataSourceTestResponse;
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

/** Admin management API for Engine-local data source registrations. */
@RestController
@RequestMapping("/api/v1/service-engine-data-sources")
public class ServiceEngineDataSourceRegistrationResource {

    private final ServiceEngineDataSourceRegistrationService service;

    public ServiceEngineDataSourceRegistrationResource(ServiceEngineDataSourceRegistrationService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('service.engine.view')")
    public PageResponse<ServiceEngineDataSourceRegistrationResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.engine.view')")
    public ServiceEngineDataSourceRegistrationResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.engine.update')")
    public ServiceEngineDataSourceRegistrationResponse create(
            @Valid @RequestBody CreateServiceEngineDataSourceRegistrationRequest request
    ) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/sync")
    @PreAuthorize("hasAuthority('service.engine.update')")
    public ServiceEngineDataSourceRegistrationResponse sync(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceEngineDataSourceActionRequest request
    ) {
        return service.sync(id);
    }

    @PostMapping("/{id}/actions/test")
    @PreAuthorize("hasAuthority('service.engine.test')")
    public ServiceEngineDataSourceTestResponse test(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceEngineDataSourceActionRequest request
    ) {
        return service.test(id);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.engine.update')")
    public void delete(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceEngineDataSourceActionRequest request
    ) {
        service.delete(id);
    }
}

package cn.superhuang.data.scalpel.business.compute.web.resource;

import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineManagementService;
import cn.superhuang.data.scalpel.business.compute.web.request.CreateComputeEngineRequest;
import cn.superhuang.data.scalpel.business.compute.web.request.DeactivateComputeEngineRequest;
import cn.superhuang.data.scalpel.business.compute.web.request.DetachComputeEngineRequest;
import cn.superhuang.data.scalpel.business.compute.web.request.UpdateComputeEngineRequest;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineResponse;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeEngineTestResponse;
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
@RequestMapping("/api/v1/compute-engines")
public class ComputeEngineResource {

    private final ComputeEngineManagementService service;

    public ComputeEngineResource(ComputeEngineManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('compute.engine.view')")
    public PageResponse<ComputeEngineResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('compute.engine.view')")
    public ComputeEngineResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('compute.engine.create')")
    public ComputeEngineResponse create(@Valid @RequestBody CreateComputeEngineRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('compute.engine.update')")
    public ComputeEngineResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateComputeEngineRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/reconfigure")
    @PreAuthorize("hasAuthority('compute.engine.update') and hasAuthority('compute.engine.manage')")
    public ComputeEngineResponse reconfigure(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateComputeEngineRequest request
    ) {
        return service.reconfigure(id, request);
    }

    @PostMapping("/{id}/actions/test")
    @PreAuthorize("hasAuthority('compute.engine.test')")
    public ComputeEngineTestResponse test(@PathVariable UUID id) {
        return service.test(id);
    }

    @PostMapping("/{id}/actions/register")
    @PreAuthorize("hasAuthority('compute.engine.manage')")
    public ComputeEngineResponse register(@PathVariable UUID id) {
        return service.register(id);
    }

    @PostMapping("/{id}/actions/drain")
    @PreAuthorize("hasAuthority('compute.engine.manage')")
    public ComputeEngineResponse drain(@PathVariable UUID id) {
        return service.drain(id);
    }

    @PostMapping("/{id}/actions/deactivate")
    @PreAuthorize("hasAuthority('compute.engine.manage')")
    public ComputeEngineResponse deactivate(
            @PathVariable UUID id,
            @RequestBody(required = false) DeactivateComputeEngineRequest request
    ) {
        return service.deactivate(id, request != null && request.force());
    }

    @PostMapping("/{id}/actions/detach")
    @PreAuthorize("hasAuthority('compute.engine.manage')")
    public ComputeEngineResponse detach(
            @PathVariable UUID id,
            @Valid @RequestBody DetachComputeEngineRequest request
    ) {
        return service.detach(id, request);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('compute.engine.delete')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}

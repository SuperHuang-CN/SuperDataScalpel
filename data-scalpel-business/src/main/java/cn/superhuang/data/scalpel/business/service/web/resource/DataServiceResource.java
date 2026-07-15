package cn.superhuang.data.scalpel.business.service.web.resource;

import cn.superhuang.data.scalpel.business.service.DataServiceManagementService;
import cn.superhuang.data.scalpel.business.service.web.request.CreateDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceResponse;
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
@RequestMapping("/api/v1/data-services")
public class DataServiceResource {

    private final DataServiceManagementService service;

    public DataServiceResource(DataServiceManagementService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('service.view')")
    public PageResponse<DataServiceResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.view')")
    public DataServiceResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.create')")
    public DataServiceResponse create(@Valid @RequestBody CreateDataServiceRequest request) {
        return service.create(request);
    }

    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('service.update')")
    public DataServiceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDataServiceRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/actions/publish")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceResponse publish(@PathVariable UUID id) {
        return service.publish(id);
    }

    @PostMapping("/{id}/actions/disable")
    @PreAuthorize("hasAuthority('service.publish')")
    public DataServiceResponse disable(@PathVariable UUID id) {
        return service.disable(id);
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.delete')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}

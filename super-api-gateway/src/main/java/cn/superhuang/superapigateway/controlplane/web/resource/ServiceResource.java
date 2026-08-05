package cn.superhuang.superapigateway.controlplane.web.resource;

import cn.superhuang.superapigateway.controlplane.execution.ControlPlaneExecutor;
import cn.superhuang.superapigateway.controlplane.service.GatewayManagementService;
import cn.superhuang.superapigateway.controlplane.web.request.ServiceRequests;
import cn.superhuang.superapigateway.controlplane.web.response.PageResponse;
import cn.superhuang.superapigateway.controlplane.web.response.ServiceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/admin-api/v1/services")
public class ServiceResource {

    private final GatewayManagementService service;
    private final ControlPlaneExecutor executor;

    public ServiceResource(GatewayManagementService service, ControlPlaneExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    @GetMapping
    public Mono<PageResponse<ServiceResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String externalId
    ) {
        return executor.execute(() -> service.listServices(page, size, source, externalId));
    }

    @GetMapping("/{id}")
    public Mono<ServiceResponse> get(@PathVariable UUID id) {
        return executor.execute(() -> service.getService(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ServiceResponse> create(@Valid @RequestBody ServiceRequests.Create request) {
        return executor.execute(() -> service.createService(request));
    }

    @PostMapping("/{id}/actions/update")
    public Mono<ServiceResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ServiceRequests.Update request
    ) {
        return executor.execute(() -> service.updateService(id, request));
    }

    @PostMapping("/{id}/actions/enable")
    public Mono<ServiceResponse> enable(@PathVariable UUID id) {
        return executor.execute(() -> service.setServiceEnabled(id, true));
    }

    @PostMapping("/{id}/actions/disable")
    public Mono<ServiceResponse> disable(@PathVariable UUID id) {
        return executor.execute(() -> service.setServiceEnabled(id, false));
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return executor.run(() -> service.deleteService(id));
    }
}

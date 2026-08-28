package cn.superhuang.superapigateway.controlplane.web.resource;

import cn.superhuang.superapigateway.controlplane.execution.ControlPlaneExecutor;
import cn.superhuang.superapigateway.controlplane.service.GatewayManagementService;
import cn.superhuang.superapigateway.controlplane.web.request.RouteRequests;
import cn.superhuang.superapigateway.controlplane.web.response.RouteResponse;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admin-api/v1/routes")
public class RouteResource {

    private final GatewayManagementService service;
    private final ControlPlaneExecutor executor;

    public RouteResource(GatewayManagementService service, ControlPlaneExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    @GetMapping
    public Mono<List<RouteResponse>> list(
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String externalId,
            @RequestParam(required = false) String pathPattern
    ) {
        return executor.execute(() -> service.listRoutes(serviceId, source, externalId, pathPattern));
    }

    @GetMapping("/{id}")
    public Mono<RouteResponse> get(@PathVariable UUID id) {
        return executor.execute(() -> service.getRoute(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<RouteResponse> create(@Valid @RequestBody RouteRequests.Create request) {
        return executor.execute(() -> service.createRoute(request));
    }

    @PostMapping("/{id}/actions/update")
    public Mono<RouteResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody RouteRequests.Update request
    ) {
        return executor.execute(() -> service.updateRoute(id, request));
    }

    @PostMapping("/{id}/actions/enable")
    public Mono<RouteResponse> enable(@PathVariable UUID id) {
        return executor.execute(() -> service.setRouteEnabled(id, true));
    }

    @PostMapping("/{id}/actions/disable")
    public Mono<RouteResponse> disable(@PathVariable UUID id) {
        return executor.execute(() -> service.setRouteEnabled(id, false));
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return executor.run(() -> service.deleteRoute(id));
    }
}

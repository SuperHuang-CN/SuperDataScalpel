package cn.superhuang.superapigateway.controlplane.web.resource;

import cn.superhuang.superapigateway.controlplane.execution.ControlPlaneExecutor;
import cn.superhuang.superapigateway.controlplane.security.AdminAuthenticationWebFilter;
import cn.superhuang.superapigateway.controlplane.service.AccessValidityService;
import cn.superhuang.superapigateway.controlplane.web.request.AccessValidityRequest;
import cn.superhuang.superapigateway.controlplane.web.response.AccessValidityResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/admin-api/v1/access-validities/{kind}/{id}")
public class AccessValidityResource {
    private final AccessValidityService service;
    private final ControlPlaneExecutor executor;
    public AccessValidityResource(AccessValidityService service, ControlPlaneExecutor executor) { this.service = service; this.executor = executor; }
    @GetMapping
    public Mono<AccessValidityResponse> get(@PathVariable String kind, @PathVariable UUID id) { return executor.execute(() -> service.get(kind, id)); }
    @PostMapping("/actions/update")
    public Mono<AccessValidityResponse> update(@PathVariable String kind, @PathVariable UUID id,
            @Valid @RequestBody AccessValidityRequest request, ServerWebExchange exchange) {
        boolean machine = Boolean.TRUE.equals(exchange.getAttribute(AdminAuthenticationWebFilter.MACHINE_ATTRIBUTE));
        return executor.execute(() -> service.update(kind, id, request, machine));
    }
}

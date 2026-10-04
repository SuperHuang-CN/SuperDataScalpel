package cn.superhuang.superapigateway.controlplane.web.resource;

import cn.superhuang.superapigateway.controlplane.execution.ControlPlaneExecutor;
import cn.superhuang.superapigateway.controlplane.security.AdminAuthenticationWebFilter;
import cn.superhuang.superapigateway.controlplane.service.GatewayTrafficPolicyService;
import cn.superhuang.superapigateway.controlplane.web.request.TrafficPolicyRequest;
import cn.superhuang.superapigateway.controlplane.web.response.TrafficPolicyResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.UUID;

@RestController
@RequestMapping("/admin-api/v1/services/{id}")
public class TrafficPolicyResource {
    private final GatewayTrafficPolicyService service;
    private final ControlPlaneExecutor executor;
    public TrafficPolicyResource(GatewayTrafficPolicyService service, ControlPlaneExecutor executor) {
        this.service = service; this.executor = executor;
    }
    @GetMapping("/traffic-policy")
    public Mono<TrafficPolicyResponse> get(@PathVariable UUID id) { return executor.execute(() -> service.get(id)); }
    @PostMapping("/actions/update-traffic-policy")
    public Mono<TrafficPolicyResponse> update(@PathVariable UUID id, @Valid @RequestBody TrafficPolicyRequest request,
                                               ServerWebExchange exchange) {
        boolean machine = Boolean.TRUE.equals(exchange.getAttribute(AdminAuthenticationWebFilter.MACHINE_ATTRIBUTE));
        return executor.execute(() -> service.update(id, request, machine));
    }
}

package cn.superhuang.superapigateway.controlplane.web.resource;

import cn.superhuang.superapigateway.controlplane.execution.ControlPlaneExecutor;
import cn.superhuang.superapigateway.controlplane.service.GatewayManagementService;
import cn.superhuang.superapigateway.controlplane.web.response.RuntimeResponses;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/admin-api/v1/runtime")
public class RuntimeResource {

    private final GatewayManagementService service;
    private final ControlPlaneExecutor executor;

    public RuntimeResource(GatewayManagementService service, ControlPlaneExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    @GetMapping
    public Mono<RuntimeResponses.Summary> summary() {
        return executor.execute(service::runtimeSummary);
    }

    @PostMapping("/actions/reload")
    public Mono<Map<String, Long>> reload() {
        return executor.execute(() -> Map.of("targetRevision", service.requestReload()));
    }
}

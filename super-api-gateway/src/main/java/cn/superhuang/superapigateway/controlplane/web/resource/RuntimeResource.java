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
    private final cn.superhuang.superapigateway.runtime.GatewayTelemetry telemetry;
    private final cn.superhuang.superapigateway.accesslog.AccessLogPublisher logs;

    public RuntimeResource(GatewayManagementService service, ControlPlaneExecutor executor,
                           cn.superhuang.superapigateway.runtime.GatewayTelemetry telemetry,
                           cn.superhuang.superapigateway.accesslog.AccessLogPublisher logs) {
        this.service = service;
        this.executor = executor;
        this.telemetry = telemetry;
        this.logs = logs;
    }

    @GetMapping
    public Mono<RuntimeResponses.Summary> summary() {
        return executor.execute(service::runtimeSummary);
    }

    @PostMapping("/actions/reload")
    public Mono<Map<String, Long>> reload() {
        return executor.execute(() -> Map.of("targetRevision", service.requestReload()));
    }

    @GetMapping("/telemetry")
    public Mono<cn.superhuang.superapigateway.controlplane.web.response.RuntimeTelemetryResponse> telemetry() {
        return executor.execute(() -> new cn.superhuang.superapigateway.controlplane.web.response.RuntimeTelemetryResponse(
                telemetry.snapshot(), logs.deliveryStatus()));
    }
}

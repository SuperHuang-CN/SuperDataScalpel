package cn.superhuang.superapigateway.controlplane.web.resource;

import cn.superhuang.superapigateway.controlplane.execution.ControlPlaneExecutor;
import cn.superhuang.superapigateway.controlplane.service.GatewayManagementService;
import cn.superhuang.superapigateway.controlplane.web.request.GrantSubscriptionRequest;
import cn.superhuang.superapigateway.controlplane.web.response.PageResponse;
import cn.superhuang.superapigateway.controlplane.web.response.SubscriptionResponse;
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
@RequestMapping("/admin-api/v1/subscriptions")
public class SubscriptionResource {

    private final GatewayManagementService service;
    private final ControlPlaneExecutor executor;

    public SubscriptionResource(GatewayManagementService service, ControlPlaneExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    @GetMapping
    public Mono<PageResponse<SubscriptionResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID consumerId,
            @RequestParam(required = false) UUID serviceId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String externalId
    ) {
        return executor.execute(
                () -> service.listSubscriptions(page, size, consumerId, serviceId, source, externalId)
        );
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<SubscriptionResponse> grant(@Valid @RequestBody GrantSubscriptionRequest request) {
        return executor.execute(() -> service.grantSubscription(request));
    }

    @PostMapping("/{id}/actions/revoke")
    public Mono<SubscriptionResponse> revoke(@PathVariable UUID id) {
        return executor.execute(() -> service.revokeSubscription(id));
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return executor.run(() -> service.deleteSubscription(id));
    }
}

package cn.superhuang.superapigateway.controlplane.web.resource;

import cn.superhuang.superapigateway.controlplane.execution.ControlPlaneExecutor;
import cn.superhuang.superapigateway.controlplane.service.GatewayManagementService;
import cn.superhuang.superapigateway.controlplane.web.request.ConsumerRequests;
import cn.superhuang.superapigateway.controlplane.web.request.CreateApiKeyRequest;
import cn.superhuang.superapigateway.controlplane.web.request.RotateApiKeyRequest;
import cn.superhuang.superapigateway.controlplane.web.response.ApiKeyDetailResponse;
import cn.superhuang.superapigateway.controlplane.web.response.ApiKeyResponse;
import cn.superhuang.superapigateway.controlplane.web.response.ConsumerResponse;
import cn.superhuang.superapigateway.controlplane.web.response.PageResponse;
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
@RequestMapping("/admin-api/v1/consumers")
public class ConsumerResource {

    private final GatewayManagementService service;
    private final ControlPlaneExecutor executor;

    public ConsumerResource(GatewayManagementService service, ControlPlaneExecutor executor) {
        this.service = service;
        this.executor = executor;
    }

    @GetMapping
    public Mono<PageResponse<ConsumerResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String externalId
    ) {
        return executor.execute(() -> service.listConsumers(page, size, source, externalId));
    }

    @GetMapping("/{id}")
    public Mono<ConsumerResponse> get(@PathVariable UUID id) {
        return executor.execute(() -> service.getConsumer(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ConsumerResponse> create(@Valid @RequestBody ConsumerRequests.Create request) {
        return executor.execute(() -> service.createConsumer(request));
    }

    @PostMapping("/{id}/actions/update")
    public Mono<ConsumerResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody ConsumerRequests.Update request
    ) {
        return executor.execute(() -> service.updateConsumer(id, request));
    }

    @PostMapping("/{id}/actions/enable")
    public Mono<ConsumerResponse> enable(@PathVariable UUID id) {
        return executor.execute(() -> service.setConsumerEnabled(id, true));
    }

    @PostMapping("/{id}/actions/disable")
    public Mono<ConsumerResponse> disable(@PathVariable UUID id) {
        return executor.execute(() -> service.setConsumerEnabled(id, false));
    }

    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> delete(@PathVariable UUID id) {
        return executor.run(() -> service.deleteConsumer(id));
    }

    @GetMapping("/{consumerId}/api-keys")
    public Mono<List<ApiKeyResponse>> listApiKeys(
            @PathVariable UUID consumerId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String externalId
    ) {
        return executor.execute(() -> service.listApiKeys(consumerId, source, externalId));
    }

    @GetMapping("/{consumerId}/api-keys/{keyId}")
    public Mono<ApiKeyDetailResponse> getApiKey(
            @PathVariable UUID consumerId,
            @PathVariable UUID keyId
    ) {
        return executor.execute(() -> service.getApiKey(consumerId, keyId));
    }

    @PostMapping("/{consumerId}/api-keys")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiKeyResponse> createApiKey(
            @PathVariable UUID consumerId,
            @Valid @RequestBody CreateApiKeyRequest request
    ) {
        return executor.execute(() -> service.createApiKey(consumerId, request));
    }

    @PostMapping("/{consumerId}/api-keys/{keyId}/actions/rotate")
    public Mono<ApiKeyResponse> rotateApiKey(
            @PathVariable UUID consumerId,
            @PathVariable UUID keyId,
            @Valid @RequestBody(required = false) RotateApiKeyRequest request
    ) {
        return executor.execute(() -> service.rotateApiKey(consumerId, keyId, request));
    }

    @PostMapping("/{consumerId}/api-keys/{keyId}/actions/revoke")
    public Mono<ApiKeyResponse> revokeApiKey(
            @PathVariable UUID consumerId,
            @PathVariable UUID keyId
    ) {
        return executor.execute(() -> service.revokeApiKey(consumerId, keyId));
    }

    @PostMapping("/{consumerId}/api-keys/{keyId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteApiKey(
            @PathVariable UUID consumerId,
            @PathVariable UUID keyId
    ) {
        return executor.run(() -> service.deleteApiKey(consumerId, keyId));
    }
}

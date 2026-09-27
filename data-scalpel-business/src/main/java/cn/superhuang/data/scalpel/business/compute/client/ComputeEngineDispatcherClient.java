package cn.superhuang.data.scalpel.business.compute.client;

import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineProperties;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionScope;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionSummaryResponse;
import cn.superhuang.data.scalpel.contract.execution.DispatcherRuntimeOverviewResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.util.UUID;

@Component
public class ComputeEngineDispatcherClient {

    private final ComputeEngineProperties properties;

    public ComputeEngineDispatcherClient(ComputeEngineProperties properties) {
        this.properties = properties;
    }

    public DispatcherInfoResponse info(String baseUrl, String token) {
        return client(baseUrl, token).get().uri("/api/v1/dispatcher/info")
                .retrieve().body(DispatcherInfoResponse.class);
    }

    public cn.superhuang.data.scalpel.contract.execution.DispatcherTargetDirectoryResponse targets(String baseUrl, String token) {
        return client(baseUrl, token).get().uri("/api/v1/dispatcher/targets").retrieve()
                .body(cn.superhuang.data.scalpel.contract.execution.DispatcherTargetDirectoryResponse.class);
    }

    public DispatcherInfoResponse targetInfo(String baseUrl, String token, String targetKey) {
        if (targetKey == null) return info(baseUrl, token);
        return client(baseUrl, token).get().uri(b -> b.path("/api/v1/dispatcher/info")
                .queryParam("targetKey", targetKey).build()).retrieve().body(DispatcherInfoResponse.class);
    }

    public DispatcherInfoResponse info(String baseUrl, String token, UUID engineId) {
        if (engineId == null) return info(baseUrl, token);
        return client(baseUrl, token).get().uri(b -> b.path("/api/v1/dispatcher/info")
                .queryParam("engineId", engineId).build()).retrieve().body(DispatcherInfoResponse.class);
    }

    public DispatcherRegistrationResponse registration(String baseUrl, String token, UUID engineId) {
        if (engineId == null) return registration(baseUrl, token);
        return client(baseUrl, token).get().uri(b -> b.path("/api/v1/dispatcher/registration")
                .queryParam("engineId", engineId).build()).retrieve().body(DispatcherRegistrationResponse.class);
    }

    public DispatcherRegistrationResponse drain(String baseUrl, String token, UUID engineId) {
        if (engineId == null) return drain(baseUrl, token);
        return client(baseUrl, token).post().uri(b -> b.path("/api/v1/dispatcher/registration/actions/drain")
                .queryParam("engineId", engineId).build()).retrieve().body(DispatcherRegistrationResponse.class);
    }

    public DispatcherRegistrationResponse resume(String baseUrl, String token, UUID engineId) {
        return client(baseUrl, token).post().uri(b -> {
            b.path("/api/v1/dispatcher/registration/actions/resume");
            if (engineId != null) b.queryParam("engineId", engineId);
            return b.build();
        }).retrieve().body(DispatcherRegistrationResponse.class);
    }

    public DispatcherRegistrationResponse deactivate(String baseUrl, String token, UUID engineId, boolean force) {
        if (engineId == null) return deactivate(baseUrl, token, force);
        return client(baseUrl, token, force).post().uri(b -> b.path("/api/v1/dispatcher/registration/actions/deactivate")
                .queryParam("engineId", engineId).build()).body(new DispatcherDeactivateRequest(force))
                .retrieve().body(DispatcherRegistrationResponse.class);
    }

    public DispatcherRuntimeOverviewResponse runtimeOverview(String baseUrl, String token, UUID engineId) {
        if (engineId == null) return runtimeOverview(baseUrl, token);
        return client(baseUrl, token).get().uri(b -> b.path("/api/v1/dispatcher/runtime-overview")
                .queryParam("engineId", engineId).build()).retrieve().body(DispatcherRuntimeOverviewResponse.class);
    }

    public PageResponse<DispatcherExecutionSummaryResponse> executions(String baseUrl, String token, UUID engineId,
            DispatcherExecutionScope scope, int page, int size) {
        if (engineId == null) return executions(baseUrl, token, scope, page, size);
        return client(baseUrl, token).get().uri(b -> b.path("/api/v1/task-executions")
                .queryParam("engineId", engineId).queryParam("scope", scope).queryParam("page", page)
                .queryParam("size", size).build()).retrieve()
                .body(new org.springframework.core.ParameterizedTypeReference<>() { });
    }

    public DispatcherRegistrationResponse registration(String baseUrl, String token) {
        return client(baseUrl, token).get().uri("/api/v1/dispatcher/registration")
                .retrieve().body(DispatcherRegistrationResponse.class);
    }

    public DispatcherRegistrationResponse activate(
            String baseUrl,
            String token,
            DispatcherRegistrationRequest request
    ) {
        return client(baseUrl, token).post().uri("/api/v1/dispatcher/registration/actions/activate")
                .body(request).retrieve().body(DispatcherRegistrationResponse.class);
    }

    public DispatcherRegistrationResponse drain(String baseUrl, String token) {
        return client(baseUrl, token).post().uri("/api/v1/dispatcher/registration/actions/drain")
                .retrieve().body(DispatcherRegistrationResponse.class);
    }

    public DispatcherRegistrationResponse deactivate(String baseUrl, String token, boolean force) {
        return client(baseUrl, token, force).post().uri("/api/v1/dispatcher/registration/actions/deactivate")
                .body(new DispatcherDeactivateRequest(force)).retrieve().body(DispatcherRegistrationResponse.class);
    }

    public DispatcherExecutionResponse execution(String baseUrl, String token, UUID executionId) {
        return client(baseUrl, token).get().uri("/api/v1/task-executions/{executionId}", executionId)
                .retrieve().body(DispatcherExecutionResponse.class);
    }

    public DispatcherExecutionLogResponse executionLog(
            String baseUrl,
            String token,
            UUID executionId,
            int attempt
    ) {
        return client(baseUrl, token).get().uri(builder -> builder
                        .path("/api/v1/task-executions/{executionId}/logs")
                        .queryParam("attempt", attempt)
                        .build(executionId))
                .retrieve().body(DispatcherExecutionLogResponse.class);
    }

    public DispatcherRuntimeOverviewResponse runtimeOverview(String baseUrl, String token) {
        return client(baseUrl, token).get().uri("/api/v1/dispatcher/runtime-overview")
                .retrieve().body(DispatcherRuntimeOverviewResponse.class);
    }

    public PageResponse<DispatcherExecutionSummaryResponse> executions(
            String baseUrl,
            String token,
            DispatcherExecutionScope scope,
            int page,
            int size
    ) {
        return client(baseUrl, token).get().uri(builder -> builder.path("/api/v1/task-executions")
                .queryParam("scope", scope).queryParam("page", page).queryParam("size", size).build())
                .retrieve().body(new org.springframework.core.ParameterizedTypeReference<>() { });
    }

    private RestClient client(String baseUrl, String token) {
        return client(baseUrl, token, false);
    }

    private RestClient client(String baseUrl, String token, boolean awaitCleanup) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        // Forced deactivation waits up to 30s by default at the Dispatcher; ordinary calls stay short.
        requestFactory.setReadTimeout(awaitCleanup && properties.requestTimeout().compareTo(java.time.Duration.ofSeconds(60)) < 0
                ? java.time.Duration.ofSeconds(60) : properties.requestTimeout());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }
}

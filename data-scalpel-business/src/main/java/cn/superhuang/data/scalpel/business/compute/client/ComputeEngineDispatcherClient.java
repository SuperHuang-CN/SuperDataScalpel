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
        return client(baseUrl, token).post().uri("/api/v1/dispatcher/registration/actions/deactivate")
                .body(new DispatcherDeactivateRequest(force)).retrieve().body(DispatcherRegistrationResponse.class);
    }

    public DispatcherExecutionResponse execution(String baseUrl, String token, UUID executionId) {
        return client(baseUrl, token).get().uri("/api/v1/task-executions/{executionId}", executionId)
                .retrieve().body(DispatcherExecutionResponse.class);
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
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.requestTimeout());
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }
}

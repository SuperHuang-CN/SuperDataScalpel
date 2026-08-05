package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRemovalRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceTestResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceEngineInfoResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceUndeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ScriptCompletionResponse;
import cn.superhuang.data.scalpel.contract.service.ScriptDraftExecutionRequest;
import cn.superhuang.data.scalpel.contract.service.ScriptDraftExecutionResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.UUID;

/** Minimal synchronous Admin-to-Engine client. The Engine owns its persisted runtime snapshot. */
@Component
public class ServiceEngineClient {

    private final ServiceEngineCredentialCipher credentialCipher;

    public ServiceEngineClient(ServiceEngineCredentialCipher credentialCipher) {
        this.credentialCipher = credentialCipher;
    }

    public ServiceEngineInfoResponse info(ServiceEngine engine) {
        return client(engine).get().uri("/internal/v1/info").retrieve().body(ServiceEngineInfoResponse.class);
    }

    public ServiceEngineInfoResponse info(String adminUrl, String managementToken) {
        return client(adminUrl, managementToken).get().uri("/internal/v1/info")
                .retrieve().body(ServiceEngineInfoResponse.class);
    }

    public ServiceDeploymentResponse deploy(ServiceEngine engine, ServiceDeploymentRequest request) {
        return client(engine).post().uri("/internal/v1/deployments").body(request)
                .retrieve().body(ServiceDeploymentResponse.class);
    }

    public ServiceDeploymentResponse remove(ServiceEngine engine, ServiceUndeploymentRequest request) {
        return client(engine).post().uri("/internal/v1/deployments/actions/remove").body(request)
                .retrieve().body(ServiceDeploymentResponse.class);
    }

    public EngineDataSourceRegistrationResponse registerDataSource(
            ServiceEngine engine,
            EngineDataSourceRegistrationRequest request
    ) {
        return client(engine).post().uri("/internal/v1/data-sources").body(request)
                .retrieve().body(EngineDataSourceRegistrationResponse.class);
    }

    public EngineDataSourceTestResponse testDataSource(ServiceEngine engine, UUID dataSourceId) {
        return client(engine).post().uri("/internal/v1/data-sources/{dataSourceId}/actions/test", dataSourceId)
                .retrieve().body(EngineDataSourceTestResponse.class);
    }

    public EngineDataSourceRegistrationResponse removeDataSource(
            ServiceEngine engine,
            EngineDataSourceRemovalRequest request
    ) {
        return client(engine).post().uri("/internal/v1/data-sources/actions/remove").body(request)
                .retrieve().body(EngineDataSourceRegistrationResponse.class);
    }

    public ScriptDraftExecutionResponse executeScriptDraft(
            ServiceEngine engine,
            ScriptDraftExecutionRequest request
    ) {
        return client(engine).post().uri("/internal/v1/scripts/actions/execute-draft").body(request)
                .retrieve().body(ScriptDraftExecutionResponse.class);
    }

    public ScriptCompletionResponse scriptCompletion(
            ServiceEngine engine,
            UUID dataSourceId
    ) {
        return client(engine).get()
                .uri(builder -> builder.path("/internal/v1/scripts/completion")
                        .queryParam("dataSourceId", dataSourceId)
                        .build())
                .retrieve().body(ScriptCompletionResponse.class);
    }

    private RestClient client(ServiceEngine engine) {
        return client(
                engine.getAdminUrl(),
                credentialCipher.decrypt(engine.getManagementTokenCiphertext())
        );
    }

    private RestClient client(String adminUrl, String managementToken) {
        try {
            return RestClient.builder()
                    .baseUrl(adminUrl)
                    .defaultHeader("Authorization", "Bearer " + managementToken)
                    .build();
        } catch (RestClientException exception) {
            throw new IllegalStateException("无法创建服务引擎调用：" + exception.getMessage(), exception);
        }
    }
}

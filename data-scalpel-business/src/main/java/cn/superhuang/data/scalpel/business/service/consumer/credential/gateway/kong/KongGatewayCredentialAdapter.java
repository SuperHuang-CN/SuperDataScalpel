package cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.kong;

import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialOperationException;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialPort;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialReference;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialResult;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialSpec;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;

@Component
public class KongGatewayCredentialAdapter implements GatewayCredentialPort {

    private static final String MANAGED_TAG = "datascalpel";
    private static final String CREDENTIAL_TAG = "datascalpel-credential";

    private final ServiceGatewayProperties properties;

    public KongGatewayCredentialAdapter(ServiceGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public GatewayProvider provider() {
        return GatewayProvider.KONG;
    }

    @Override
    public GatewayCredentialResult upsert(GatewayCredentialSpec credential) {
        try {
            RestClient client = client();
            String consumer = required(
                    credential.consumerExternalId(), "Kong Consumer 外部 ID"
            );
            KongKeyAuthCredentialResponse existing = findOwned(client, consumer, credential.id().toString());
            KongKeyAuthCredentialRequest request = new KongKeyAuthCredentialRequest(
                    required(credential.secret(), "API Key"),
                    tags(credential.id().toString(), existing == null ? List.of() : existing.tags())
            );
            KongKeyAuthCredentialResponse result;
            if (existing == null) {
                result = create(client, consumer, request);
                if (result == null) {
                    result = findOwned(client, consumer, credential.id().toString());
                }
            } else {
                verifyOwnership(credential, consumer, existing);
                result = patch(client, consumer, existing.id(), request);
                if (result == null) {
                    result = create(client, consumer, request);
                }
            }
            if (result == null) {
                throw new GatewayCredentialOperationException("Kong API Key 同步后无法读取");
            }
            verifyOwnership(credential, consumer, result);
            return new GatewayCredentialResult(result.id());
        } catch (GatewayCredentialOperationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GatewayCredentialOperationException(
                    "Kong API Key 同步请求失败：" + safeCauseMessage(exception),
                    exception
            );
        }
    }

    @Override
    public void remove(GatewayCredentialReference credential) {
        try {
            RestClient client = client();
            String consumer = hasText(credential.consumerExternalId())
                    ? credential.consumerExternalId()
                    : credential.consumerCode();
            KongKeyAuthCredentialResponse existing = hasText(credential.externalId())
                    ? find(client, consumer, credential.externalId())
                    : findOwned(client, consumer, credential.id().toString());
            if (existing == null) return;
            verifyOwnership(credential.id().toString(), consumer, existing);
            client.delete()
                    .uri("/consumers/{consumer}/key-auth/{credential}", consumer, existing.id())
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 404
                                || response.getStatusCode().is2xxSuccessful()) {
                            return null;
                        }
                        throw error("删除", response);
                    });
        } catch (GatewayCredentialOperationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GatewayCredentialOperationException(
                    "Kong API Key 删除请求失败：" + safeCauseMessage(exception),
                    exception
            );
        }
    }

    @Override
    public GatewayInspectionResult inspect(GatewayCredentialInspectionSpec inspection) {
        try {
            RestClient client = client();
            String consumer = required(inspection.consumerExternalId(), "Kong Consumer 外部 ID");
            KongKeyAuthCredentialResponse actual = hasText(inspection.externalId())
                    ? find(client, consumer, inspection.externalId())
                    : findOwned(client, consumer, inspection.id().toString());
            if (actual == null && hasText(inspection.externalId())) {
                actual = findOwned(client, consumer, inspection.id().toString());
            }
            if (!inspection.expectedPresent()) {
                if (actual == null) {
                    return GatewayInspectionResult.inSync("Kong API Key 已不存在，符合本地删除期望");
                }
                if (!isOwned(inspection.id().toString(), consumer, actual)) {
                    return GatewayInspectionResult.drifted(
                            GatewayReconciliationReason.OWNER_MISMATCH,
                            "绑定 ID 指向的 Kong API Key 不属于当前凭证"
                    );
                }
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.UNEXPECTED_REMOTE,
                        "本地期望删除，但 Kong API Key 仍然存在"
                );
            }
            if (actual == null) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.REMOTE_MISSING,
                        "Kong API Key 不存在；本地不保存明文，必须轮换后才能恢复"
                );
            }
            if (!isOwned(inspection.id().toString(), consumer, actual)) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.OWNER_MISMATCH,
                        "Kong API Key 归属标识或所属 Consumer 与本地绑定不一致"
                );
            }
            if (hasText(inspection.externalId()) && !inspection.externalId().equals(actual.id())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.CONFIG_MISMATCH,
                        "Kong API Key 外部 ID 与本地绑定不一致"
                );
            }
            if (!inspection.secretDigest().equalsIgnoreCase(sha256(actual.key()))) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.SECRET_MISMATCH,
                        "Kong API Key 与本地保存的密钥摘要不一致，必须轮换凭证"
                );
            }
            return GatewayInspectionResult.inSync("Kong API Key 与本地期望一致");
        } catch (GatewayCredentialOperationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GatewayCredentialOperationException(
                    "Kong API Key 状态检查失败：" + safeCauseMessage(exception),
                    exception
            );
        }
    }

    private static KongKeyAuthCredentialResponse findOwned(
            RestClient client,
            String consumer,
            String credentialId
    ) {
        KongKeyAuthCredentialListResponse response = client.get()
                .uri("/consumers/{consumer}/key-auth?size=100", consumer)
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, httpResponse) -> {
                    if (httpResponse.getStatusCode().value() == 404) return null;
                    if (!httpResponse.getStatusCode().is2xxSuccessful()) {
                        throw error("查询", httpResponse);
                    }
                    return httpResponse.bodyTo(KongKeyAuthCredentialListResponse.class);
                });
        if (response == null || response.data() == null) return null;
        String ownerTag = ownerTag(credentialId);
        return response.data().stream()
                .filter(candidate -> candidate.tags() != null && candidate.tags().contains(ownerTag))
                .findFirst()
                .orElse(null);
    }

    private static KongKeyAuthCredentialResponse find(
            RestClient client,
            String consumer,
            String credential
    ) {
        return client.get()
                .uri("/consumers/{consumer}/key-auth/{credential}", consumer, credential)
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 404) return null;
                    if (!response.getStatusCode().is2xxSuccessful()) throw error("查询", response);
                    return response.bodyTo(KongKeyAuthCredentialResponse.class);
                });
    }

    private static KongKeyAuthCredentialResponse create(
            RestClient client,
            String consumer,
            KongKeyAuthCredentialRequest request
    ) {
        return client.post()
                .uri("/consumers/{consumer}/key-auth", consumer)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().value() == 409) return null;
                    if (!response.getStatusCode().is2xxSuccessful()) throw error("创建", response);
                    return response.bodyTo(KongKeyAuthCredentialResponse.class);
                });
    }

    private static KongKeyAuthCredentialResponse patch(
            RestClient client,
            String consumer,
            String credential,
            KongKeyAuthCredentialRequest request
    ) {
        return client.patch()
                .uri("/consumers/{consumer}/key-auth/{credential}", consumer, credential)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().value() == 404) return null;
                    if (!response.getStatusCode().is2xxSuccessful()) throw error("轮换", response);
                    return response.bodyTo(KongKeyAuthCredentialResponse.class);
                });
    }

    private static void verifyOwnership(
            GatewayCredentialSpec expected,
            String consumer,
            KongKeyAuthCredentialResponse actual
    ) {
        verifyOwnership(expected.id().toString(), consumer, actual);
    }

    private static boolean isOwned(
            String credentialId,
            String consumer,
            KongKeyAuthCredentialResponse actual
    ) {
        return actual != null
                && hasText(actual.id())
                && actual.tags() != null
                && actual.tags().contains(MANAGED_TAG)
                && actual.tags().contains(CREDENTIAL_TAG)
                && actual.tags().contains(ownerTag(credentialId))
                && actual.consumer() != null
                && consumer.equals(actual.consumer().id());
    }

    private static void verifyOwnership(
            String credentialId,
            String consumer,
            KongKeyAuthCredentialResponse actual
    ) {
        if (actual == null || !hasText(actual.id())
                || actual.tags() == null
                || !actual.tags().contains(MANAGED_TAG)
                || !actual.tags().contains(CREDENTIAL_TAG)
                || !actual.tags().contains(ownerTag(credentialId))
                || actual.consumer() == null
                || !consumer.equals(actual.consumer().id())) {
            throw new GatewayCredentialOperationException("Kong API Key 归属校验失败，拒绝接管或删除");
        }
    }

    private static List<String> tags(String credentialId, List<String> existing) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (existing != null) tags.addAll(existing);
        tags.add(MANAGED_TAG);
        tags.add(CREDENTIAL_TAG);
        tags.add(ownerTag(credentialId));
        return List.copyOf(tags);
    }

    private RestClient client() {
        ServiceGatewayProperties.Kong kong = properties.kong();
        String adminUrl = normalizeHttpUrl(kong.adminUrl());
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(kong.connectTimeout()).build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(kong.requestTimeout());
        return RestClient.builder().baseUrl(adminUrl).requestFactory(requestFactory).build();
    }

    private static String normalizeHttpUrl(String value) {
        String normalized = required(value, "Kong Admin URL").replaceFirst("/+$", "");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new GatewayCredentialOperationException("Kong Admin URL 格式不正确", exception);
        }
        if (uri.getHost() == null
                || !"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new GatewayCredentialOperationException("Kong Admin URL 必须是 HTTP 或 HTTPS 地址");
        }
        return normalized;
    }

    private static GatewayCredentialOperationException error(
            String action,
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response
    ) {
        int status;
        try {
            status = response.getStatusCode().value();
        } catch (IOException exception) {
            return new GatewayCredentialOperationException(
                    "Kong API Key " + action + "失败，无法读取 HTTP 状态", exception
            );
        }
        String detail = null;
        try {
            KongCredentialErrorResponse body = response.bodyTo(KongCredentialErrorResponse.class);
            if (body != null) detail = hasText(body.message()) ? body.message() : body.name();
        } catch (RuntimeException ignored) {
            // Unknown provider fields are intentionally not persisted.
        }
        return new GatewayCredentialOperationException(
                "Kong API Key " + action + "失败（HTTP " + status + "）"
                        + (hasText(detail) ? "：" + limit(detail, 300) : "")
        );
    }

    private static String ownerTag(String credentialId) {
        return "datascalpel-credential-" + credentialId;
    }

    private static String required(String value, String label) {
        if (!hasText(value)) throw new GatewayCredentialOperationException(label + " 未配置");
        return value.trim();
    }

    private static String safeCauseMessage(Exception exception) {
        return hasText(exception.getMessage()) ? limit(exception.getMessage(), 300) : "远程调用失败";
    }

    private static String sha256(String value) {
        if (!hasText(value)) return "";
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String limit(String value, int maximumLength) {
        return value.substring(0, Math.min(maximumLength, value.length()));
    }
}

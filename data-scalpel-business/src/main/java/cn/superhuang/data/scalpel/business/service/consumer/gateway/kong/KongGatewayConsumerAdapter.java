package cn.superhuang.data.scalpel.business.service.consumer.gateway.kong;

import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerOperationException;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerPort;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerReference;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerResult;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerSpec;
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
import java.util.LinkedHashSet;
import java.util.List;

/** Kong Admin API implementation of the provider-neutral consumer capability. */
@Component
public class KongGatewayConsumerAdapter implements GatewayConsumerPort {

    private static final String MANAGED_TAG = "datascalpel";
    private static final String CONSUMER_TAG = "datascalpel-consumer";

    private final ServiceGatewayProperties properties;

    public KongGatewayConsumerAdapter(ServiceGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public GatewayProvider provider() {
        return GatewayProvider.KONG;
    }

    @Override
    public GatewayConsumerResult upsert(GatewayConsumerSpec consumer) {
        try {
            RestClient client = client();
            KongConsumerResponse existing = find(client, consumer.code());
            KongConsumerResponse synchronizedConsumer;
            if (existing == null) {
                synchronizedConsumer = create(client, request(consumer, List.of()));
                if (synchronizedConsumer == null) {
                    existing = find(client, consumer.code());
                    if (existing == null) {
                        throw new GatewayConsumerOperationException("Kong 消费者创建冲突后仍无法读取");
                    }
                    synchronizedConsumer = updateOwned(client, consumer, existing);
                }
            } else {
                synchronizedConsumer = updateOwned(client, consumer, existing);
            }
            verifySynchronized(consumer, synchronizedConsumer);
            return new GatewayConsumerResult(synchronizedConsumer.id());
        } catch (GatewayConsumerOperationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GatewayConsumerOperationException(
                    "Kong 消费者同步请求失败：" + safeCauseMessage(exception),
                    exception
            );
        }
    }

    @Override
    public void remove(GatewayConsumerReference consumer) {
        try {
            RestClient client = client();
            boolean hasExternalId = consumer.externalId() != null && !consumer.externalId().isBlank();
            KongConsumerResponse existing = find(client, hasExternalId ? consumer.externalId() : consumer.code());
            if (existing == null) {
                return;
            }
            if (!consumer.id().toString().equals(existing.customId())) {
                if (!hasExternalId) {
                    return;
                }
                throw new GatewayConsumerOperationException("Kong 消费者归属校验失败，拒绝删除");
            }
            delete(client, existing.id());
        } catch (GatewayConsumerOperationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GatewayConsumerOperationException(
                    "Kong 消费者删除请求失败：" + safeCauseMessage(exception),
                    exception
            );
        }
    }

    @Override
    public GatewayInspectionResult inspect(GatewayConsumerInspectionSpec inspection) {
        try {
            RestClient client = client();
            GatewayConsumerSpec expected = inspection.expected();
            String externalId = inspection.reference().externalId();
            KongConsumerResponse actual = hasText(externalId)
                    ? find(client, externalId)
                    : find(client, expected.code());
            if (actual == null && hasText(externalId)) {
                actual = find(client, expected.code());
            }
            if (!inspection.expectedPresent()) {
                if (actual == null) {
                    return GatewayInspectionResult.inSync("Kong Consumer 已不存在，符合本地删除期望");
                }
                if (!isOwned(expected, actual)) {
                    return GatewayInspectionResult.drifted(
                            GatewayReconciliationReason.OWNER_MISMATCH,
                            "发现同名或绑定 ID 指向的 Kong Consumer，但归属不属于当前消费者"
                    );
                }
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.UNEXPECTED_REMOTE,
                        "本地期望删除，但 Kong Consumer 仍然存在"
                );
            }
            if (actual == null) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.REMOTE_MISSING,
                        "Kong Consumer 不存在"
                );
            }
            if (!isOwned(expected, actual)) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.OWNER_MISMATCH,
                        "Kong Consumer 归属标识与当前消费者不一致"
                );
            }
            if (!expected.code().equals(actual.username())
                    || hasText(externalId) && !externalId.equals(actual.id())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.CONFIG_MISMATCH,
                        "Kong Consumer 的标识或编码与本地绑定不一致"
                );
            }
            return GatewayInspectionResult.inSync("Kong Consumer 与本地期望一致");
        } catch (GatewayConsumerOperationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GatewayConsumerOperationException(
                    "Kong 消费者状态检查失败：" + safeCauseMessage(exception),
                    exception
            );
        }
    }

    private KongConsumerResponse updateOwned(
            RestClient client,
            GatewayConsumerSpec consumer,
            KongConsumerResponse existing
    ) {
        verifyOwnership(consumer, existing);
        KongConsumerResponse updated = patch(client, existing.id(), request(consumer, existing.tags()));
        if (updated != null) {
            return updated;
        }
        KongConsumerResponse recreated = create(client, request(consumer, existing.tags()));
        if (recreated != null) {
            return recreated;
        }
        KongConsumerResponse reloaded = find(client, consumer.code());
        if (reloaded == null) {
            throw new GatewayConsumerOperationException("Kong 消费者更新后无法读取");
        }
        verifyOwnership(consumer, reloaded);
        return reloaded;
    }

    private static void verifyOwnership(GatewayConsumerSpec consumer, KongConsumerResponse existing) {
        if (!consumer.id().toString().equals(existing.customId())) {
            throw new GatewayConsumerOperationException(
                    "Kong 中已存在编码为 " + consumer.code() + " 的非 DataScalpel 消费者，拒绝接管"
            );
        }
    }

    private static boolean isOwned(GatewayConsumerSpec expected, KongConsumerResponse actual) {
        return actual != null
                && expected.id().toString().equals(actual.customId())
                && actual.tags() != null
                && actual.tags().contains(MANAGED_TAG)
                && actual.tags().contains(CONSUMER_TAG)
                && actual.tags().contains("datascalpel-consumer-" + expected.id());
    }

    private static void verifySynchronized(
            GatewayConsumerSpec expected,
            KongConsumerResponse actual
    ) {
        if (actual == null || actual.id() == null || actual.id().isBlank()
                || !expected.code().equals(actual.username())
                || !expected.id().toString().equals(actual.customId())) {
            throw new GatewayConsumerOperationException("Kong 未返回匹配的消费者同步结果");
        }
    }

    private KongConsumerRequest request(GatewayConsumerSpec consumer, List<String> existingTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (existingTags != null) {
            tags.addAll(existingTags);
        }
        tags.add(MANAGED_TAG);
        tags.add(CONSUMER_TAG);
        tags.add("datascalpel-consumer-" + consumer.id());
        return new KongConsumerRequest(consumer.code(), consumer.id().toString(), List.copyOf(tags));
    }

    private static KongConsumerResponse find(RestClient client, String consumer) {
        return client.get()
                .uri("/consumers/{consumer}", consumer)
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        return null;
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw error("查询", response);
                    }
                    return response.bodyTo(KongConsumerResponse.class);
                });
    }

    private static KongConsumerResponse create(RestClient client, KongConsumerRequest request) {
        return client.post()
                .uri("/consumers")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().value() == 409) {
                        return null;
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw error("创建", response);
                    }
                    return response.bodyTo(KongConsumerResponse.class);
                });
    }

    private static KongConsumerResponse patch(
            RestClient client,
            String externalId,
            KongConsumerRequest request
    ) {
        return client.patch()
                .uri("/consumers/{consumer}", externalId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        return null;
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw error("更新", response);
                    }
                    return response.bodyTo(KongConsumerResponse.class);
                });
    }

    private static void delete(RestClient client, String externalId) {
        client.delete()
                .uri("/consumers/{consumer}", externalId)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 404
                            || response.getStatusCode().is2xxSuccessful()) {
                        return null;
                    }
                    throw error("删除", response);
                });
    }

    private RestClient client() {
        ServiceGatewayProperties.Kong kong = properties.kong();
        String adminUrl = normalizeAdminUrl(kong.adminUrl());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(kong.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(kong.requestTimeout());
        return RestClient.builder()
                .baseUrl(adminUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private static String normalizeAdminUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new GatewayConsumerOperationException("Kong Admin URL 未配置");
        }
        String normalized = value.trim().replaceFirst("/+$", "");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new GatewayConsumerOperationException("Kong Admin URL 格式不正确", exception);
        }
        if (uri.getHost() == null
                || !"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new GatewayConsumerOperationException("Kong Admin URL 必须是 HTTP 或 HTTPS 地址");
        }
        return normalized;
    }

    private static GatewayConsumerOperationException error(
            String action,
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response
    ) {
        int status;
        try {
            status = response.getStatusCode().value();
        } catch (IOException exception) {
            return new GatewayConsumerOperationException("Kong 消费者" + action + "失败，无法读取 HTTP 状态", exception);
        }
        String detail = null;
        try {
            KongErrorResponse error = response.bodyTo(KongErrorResponse.class);
            if (error != null) {
                detail = error.message() == null || error.message().isBlank() ? error.name() : error.message();
            }
        } catch (RuntimeException ignored) {
            // Keep the external response body out of logs and persisted synchronization errors.
        }
        String suffix = detail == null || detail.isBlank() ? "" : "：" + limit(detail, 300);
        return new GatewayConsumerOperationException("Kong 消费者" + action + "失败（HTTP " + status + "）" + suffix);
    }

    private static String safeCauseMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "远程调用失败" : limit(message, 300);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String limit(String value, int maximumLength) {
        return value.substring(0, Math.min(maximumLength, value.length()));
    }
}

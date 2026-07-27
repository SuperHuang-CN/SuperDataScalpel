package cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.kong;

import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionOperationException;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionPort;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionReference;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionResult;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionSpec;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import cn.superhuang.data.scalpel.business.service.gateway.kong.KongGatewayManagedNames;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class KongGatewaySubscriptionAdapter implements GatewaySubscriptionPort {

    private static final String MANAGED_TAG = "datascalpel";
    private static final String SUBSCRIPTION_TAG = "datascalpel-subscription";

    private final ServiceGatewayProperties properties;

    public KongGatewaySubscriptionAdapter(ServiceGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public GatewayProvider provider() {
        return GatewayProvider.KONG;
    }

    @Override
    public GatewaySubscriptionResult grant(GatewaySubscriptionSpec subscription) {
        try {
            RestClient client = client();
            String consumer = required(subscription.consumerExternalId(), "Kong Consumer 外部 ID");
            String group = KongGatewayManagedNames.serviceAclGroup(subscription.dataServiceId());
            KongAclMembershipResponse existing = findOwned(client, consumer, subscription.id().toString());
            if (existing != null) {
                verifyOwnership(subscription.id().toString(), consumer, group, existing);
                return new GatewaySubscriptionResult(existing.id());
            }
            KongAclMembershipRequest request = new KongAclMembershipRequest(
                    group,
                    tags(subscription.id().toString(), List.of())
            );
            KongAclMembershipResponse created = create(client, consumer, request);
            if (created == null) {
                created = findOwned(client, consumer, subscription.id().toString());
                if (created == null) {
                    created = findByGroup(client, consumer, group);
                }
            }
            if (created == null) {
                throw new GatewaySubscriptionOperationException("Kong ACL 授权冲突后仍无法读取");
            }
            verifyOwnership(subscription.id().toString(), consumer, group, created);
            return new GatewaySubscriptionResult(created.id());
        } catch (GatewaySubscriptionOperationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GatewaySubscriptionOperationException(
                    "Kong ACL 授权请求失败：" + safeCauseMessage(exception),
                    exception
            );
        }
    }

    @Override
    public void revoke(GatewaySubscriptionReference subscription) {
        try {
            RestClient client = client();
            String consumer = hasText(subscription.consumerExternalId())
                    ? subscription.consumerExternalId()
                    : subscription.consumerCode();
            String group = KongGatewayManagedNames.serviceAclGroup(subscription.dataServiceId());
            KongAclMembershipResponse existing = hasText(subscription.externalMembershipId())
                    ? find(client, consumer, subscription.externalMembershipId())
                    : findOwned(client, consumer, subscription.id().toString());
            if (existing == null) return;
            verifyOwnership(subscription.id().toString(), consumer, group, existing);
            client.delete()
                    .uri("/consumers/{consumer}/acls/{acl}", consumer, existing.id())
                    .exchange((request, response) -> {
                        if (response.getStatusCode().value() == 404
                                || response.getStatusCode().is2xxSuccessful()) {
                            return null;
                        }
                        throw error("撤回", response);
                    });
        } catch (GatewaySubscriptionOperationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GatewaySubscriptionOperationException(
                    "Kong ACL 撤回请求失败：" + safeCauseMessage(exception),
                    exception
            );
        }
    }

    @Override
    public GatewayInspectionResult inspect(GatewaySubscriptionInspectionSpec inspection) {
        try {
            RestClient client = client();
            GatewaySubscriptionSpec expected = inspection.expected();
            String consumer = required(expected.consumerExternalId(), "Kong Consumer 外部 ID");
            String group = KongGatewayManagedNames.serviceAclGroup(expected.dataServiceId());
            KongAclMembershipResponse actual = hasText(inspection.externalMembershipId())
                    ? find(client, consumer, inspection.externalMembershipId())
                    : findOwned(client, consumer, expected.id().toString());
            if (actual == null && hasText(inspection.externalMembershipId())) {
                actual = findOwned(client, consumer, expected.id().toString());
            }
            if (!inspection.expectedPresent()) {
                if (actual == null) {
                    return GatewayInspectionResult.inSync("Kong ACL 授权已不存在，符合本地撤回期望");
                }
                if (!isOwned(expected.id().toString(), consumer, group, actual)) {
                    return GatewayInspectionResult.drifted(
                            GatewayReconciliationReason.OWNER_MISMATCH,
                            "绑定 ID 指向的 Kong ACL membership 不属于当前订阅"
                    );
                }
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.UNEXPECTED_REMOTE,
                        "本地期望撤回，但 Kong ACL 授权仍然存在"
                );
            }
            if (actual == null) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.REMOTE_MISSING,
                        "Kong ACL 授权不存在"
                );
            }
            if (!isOwned(expected.id().toString(), consumer, group, actual)) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.OWNER_MISMATCH,
                        "Kong ACL membership 的归属、Consumer 或访问组与本地绑定不一致"
                );
            }
            if (hasText(inspection.externalMembershipId())
                    && !inspection.externalMembershipId().equals(actual.id())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.CONFIG_MISMATCH,
                        "Kong ACL membership 外部 ID 与本地绑定不一致"
                );
            }
            return GatewayInspectionResult.inSync("Kong ACL 授权与本地期望一致");
        } catch (GatewaySubscriptionOperationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GatewaySubscriptionOperationException(
                    "Kong ACL 状态检查失败：" + safeCauseMessage(exception),
                    exception
            );
        }
    }

    private static KongAclMembershipResponse findOwned(
            RestClient client,
            String consumer,
            String subscriptionId
    ) {
        String tag = ownerTag(subscriptionId);
        return findInPages(client, consumer, tag, candidate ->
                candidate.tags() != null && candidate.tags().contains(tag)
        );
    }

    private static KongAclMembershipResponse findByGroup(
            RestClient client,
            String consumer,
            String group
    ) {
        return findInPages(client, consumer, null, candidate -> group.equals(candidate.group()));
    }

    private static KongAclMembershipResponse findInPages(
            RestClient client,
            String consumer,
            String tag,
            java.util.function.Predicate<KongAclMembershipResponse> predicate
    ) {
        String offset = null;
        Set<String> seenOffsets = new HashSet<>();
        do {
            String currentOffset = offset;
            KongAclMembershipListResponse response = client.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder
                                .path("/consumers/{consumer}/acls")
                                .queryParam("size", 100);
                        if (hasText(tag)) builder.queryParam("tags", tag);
                        if (hasText(currentOffset)) builder.queryParam("offset", currentOffset);
                        return builder.build(consumer);
                    })
                    .accept(MediaType.APPLICATION_JSON)
                    .exchange((request, httpResponse) -> {
                        if (httpResponse.getStatusCode().value() == 404) return null;
                        if (!httpResponse.getStatusCode().is2xxSuccessful()) {
                            throw error("查询", httpResponse);
                        }
                        return httpResponse.bodyTo(KongAclMembershipListResponse.class);
                    });
            if (response == null) return null;
            if (response.data() != null) {
                KongAclMembershipResponse match = response.data().stream()
                        .filter(predicate)
                        .findFirst()
                        .orElse(null);
                if (match != null) return match;
            }
            offset = response.offset();
            if (hasText(offset) && !seenOffsets.add(offset)) {
                throw new GatewaySubscriptionOperationException("Kong ACL 分页游标重复，无法继续查询");
            }
        } while (hasText(offset));
        return null;
    }

    private static KongAclMembershipResponse find(
            RestClient client,
            String consumer,
            String membership
    ) {
        return client.get()
                .uri("/consumers/{consumer}/acls/{acl}", consumer, membership)
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 404) return null;
                    if (!response.getStatusCode().is2xxSuccessful()) throw error("查询", response);
                    return response.bodyTo(KongAclMembershipResponse.class);
                });
    }

    private static KongAclMembershipResponse create(
            RestClient client,
            String consumer,
            KongAclMembershipRequest request
    ) {
        return client.post()
                .uri("/consumers/{consumer}/acls", consumer)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().value() == 409) return null;
                    if (!response.getStatusCode().is2xxSuccessful()) throw error("授权", response);
                    return response.bodyTo(KongAclMembershipResponse.class);
                });
    }

    private static void verifyOwnership(
            String subscriptionId,
            String consumer,
            String group,
            KongAclMembershipResponse actual
    ) {
        if (actual == null || !hasText(actual.id())
                || !group.equals(actual.group())
                || actual.tags() == null
                || !actual.tags().contains(MANAGED_TAG)
                || !actual.tags().contains(SUBSCRIPTION_TAG)
                || !actual.tags().contains(ownerTag(subscriptionId))
                || actual.consumer() == null
                || !consumer.equals(actual.consumer().id())) {
            throw new GatewaySubscriptionOperationException(
                    "Kong ACL membership 归属校验失败，拒绝接管或删除"
            );
        }
    }

    private static boolean isOwned(
            String subscriptionId,
            String consumer,
            String group,
            KongAclMembershipResponse actual
    ) {
        return actual != null
                && hasText(actual.id())
                && group.equals(actual.group())
                && actual.tags() != null
                && actual.tags().contains(MANAGED_TAG)
                && actual.tags().contains(SUBSCRIPTION_TAG)
                && actual.tags().contains(ownerTag(subscriptionId))
                && actual.consumer() != null
                && consumer.equals(actual.consumer().id());
    }

    private static List<String> tags(String subscriptionId, List<String> existing) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (existing != null) tags.addAll(existing);
        tags.add(MANAGED_TAG);
        tags.add(SUBSCRIPTION_TAG);
        tags.add(ownerTag(subscriptionId));
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
            throw new GatewaySubscriptionOperationException("Kong Admin URL 格式不正确", exception);
        }
        if (uri.getHost() == null
                || !"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new GatewaySubscriptionOperationException("Kong Admin URL 必须是 HTTP 或 HTTPS 地址");
        }
        return normalized;
    }

    private static GatewaySubscriptionOperationException error(
            String action,
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response
    ) {
        int status;
        try {
            status = response.getStatusCode().value();
        } catch (IOException exception) {
            return new GatewaySubscriptionOperationException(
                    "Kong ACL " + action + "失败，无法读取 HTTP 状态", exception
            );
        }
        String detail = null;
        try {
            KongSubscriptionErrorResponse body = response.bodyTo(KongSubscriptionErrorResponse.class);
            if (body != null) detail = hasText(body.message()) ? body.message() : body.name();
        } catch (RuntimeException ignored) {
            // Unknown provider fields are intentionally not persisted.
        }
        return new GatewaySubscriptionOperationException(
                "Kong ACL " + action + "失败（HTTP " + status + "）"
                        + (hasText(detail) ? "：" + limit(detail, 300) : "")
        );
    }

    private static String ownerTag(String subscriptionId) {
        return "datascalpel-subscription-" + subscriptionId;
    }

    private static String required(String value, String label) {
        if (!hasText(value)) throw new GatewaySubscriptionOperationException(label + " 未配置");
        return value.trim();
    }

    private static String safeCauseMessage(Exception exception) {
        return hasText(exception.getMessage()) ? limit(exception.getMessage(), 300) : "远程调用失败";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String limit(String value, int maximumLength) {
        return value.substring(0, Math.min(maximumLength, value.length()));
    }
}

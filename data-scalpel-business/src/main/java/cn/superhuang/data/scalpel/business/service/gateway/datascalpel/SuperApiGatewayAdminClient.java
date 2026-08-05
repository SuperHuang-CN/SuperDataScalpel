package cn.superhuang.data.scalpel.business.service.gateway.datascalpel;

import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.ApiKeyDetailResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.ApiKeyResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.ConsumerResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.CreateApiKeyRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.CreateConsumerRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.CreateRouteRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.CreateServiceRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.GrantSubscriptionRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.PageResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.ProblemResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.RotateApiKeyRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.RouteResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.ServiceResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.SubscriptionResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.UpdateConsumerRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.UpdateRouteRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.UpdateServiceRequest;

@Component
final class SuperApiGatewayAdminClient {

    static final String SOURCE = "DATASCALPEL";
    private static final String MACHINE_TOKEN_HEADER = "X-Super-Gateway-Admin-Token";
    private static final ParameterizedTypeReference<PageResponse<ServiceResponse>> SERVICE_PAGE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<PageResponse<ConsumerResponse>> CONSUMER_PAGE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<PageResponse<SubscriptionResponse>> SUBSCRIPTION_PAGE =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<RouteResponse>> ROUTE_LIST =
            new ParameterizedTypeReference<>() {
            };
    private static final ParameterizedTypeReference<List<ApiKeyResponse>> API_KEY_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final ServiceGatewayProperties properties;

    SuperApiGatewayAdminClient(ServiceGatewayProperties properties) {
        this.properties = properties;
    }

    Optional<ServiceResponse> findService(String externalId) {
        PageResponse<ServiceResponse> page = exchange(
                "查询 Service",
                client().get().uri(uri -> uri.path("/admin-api/v1/services")
                        .queryParam("page", 0)
                        .queryParam("size", 2)
                        .queryParam("source", SOURCE)
                        .queryParam("externalId", externalId)
                        .build()),
                SERVICE_PAGE
        );
        return unique(page == null ? null : page.content(), "Service");
    }

    ServiceResponse getService(UUID id) {
        return exchange(
                "查询 Service",
                client().get().uri("/admin-api/v1/services/{id}", id),
                ServiceResponse.class
        );
    }

    ServiceResponse createService(CreateServiceRequest request) {
        return exchange(
                "创建 Service",
                client().post().uri("/admin-api/v1/services").body(request),
                ServiceResponse.class
        );
    }

    ServiceResponse updateService(UUID id, UpdateServiceRequest request) {
        return exchange(
                "更新 Service",
                client().post().uri("/admin-api/v1/services/{id}/actions/update", id).body(request),
                ServiceResponse.class
        );
    }

    ServiceResponse setServiceEnabled(UUID id, boolean enabled) {
        return exchange(
                enabled ? "启用 Service" : "停用 Service",
                client().post().uri(
                        "/admin-api/v1/services/{id}/actions/{action}",
                        id,
                        enabled ? "enable" : "disable"
                ),
                ServiceResponse.class
        );
    }

    void deleteService(UUID id) {
        exchangeVoid(
                "删除 Service",
                client().post().uri("/admin-api/v1/services/{id}/actions/delete", id)
        );
    }

    Optional<RouteResponse> findRoute(String externalId) {
        List<RouteResponse> routes = exchange(
                "查询 Route",
                client().get().uri(uri -> uri.path("/admin-api/v1/routes")
                        .queryParam("source", SOURCE)
                        .queryParam("externalId", externalId)
                        .build()),
                ROUTE_LIST
        );
        return unique(routes, "Route");
    }

    RouteResponse createRoute(CreateRouteRequest request) {
        return exchange(
                "创建 Route",
                client().post().uri("/admin-api/v1/routes").body(request),
                RouteResponse.class
        );
    }

    RouteResponse updateRoute(UUID id, UpdateRouteRequest request) {
        return exchange(
                "更新 Route",
                client().post().uri("/admin-api/v1/routes/{id}/actions/update", id).body(request),
                RouteResponse.class
        );
    }

    RouteResponse setRouteEnabled(UUID id, boolean enabled) {
        return exchange(
                enabled ? "启用 Route" : "停用 Route",
                client().post().uri(
                        "/admin-api/v1/routes/{id}/actions/{action}",
                        id,
                        enabled ? "enable" : "disable"
                ),
                RouteResponse.class
        );
    }

    void deleteRoute(UUID id) {
        exchangeVoid(
                "删除 Route",
                client().post().uri("/admin-api/v1/routes/{id}/actions/delete", id)
        );
    }

    Optional<ConsumerResponse> findConsumer(String externalId) {
        PageResponse<ConsumerResponse> page = exchange(
                "查询 Consumer",
                client().get().uri(uri -> uri.path("/admin-api/v1/consumers")
                        .queryParam("page", 0)
                        .queryParam("size", 2)
                        .queryParam("source", SOURCE)
                        .queryParam("externalId", externalId)
                        .build()),
                CONSUMER_PAGE
        );
        return unique(page == null ? null : page.content(), "Consumer");
    }

    ConsumerResponse getConsumer(UUID id) {
        return exchange(
                "查询 Consumer",
                client().get().uri("/admin-api/v1/consumers/{id}", id),
                ConsumerResponse.class
        );
    }

    ConsumerResponse createConsumer(CreateConsumerRequest request) {
        return exchange(
                "创建 Consumer",
                client().post().uri("/admin-api/v1/consumers").body(request),
                ConsumerResponse.class
        );
    }

    ConsumerResponse updateConsumer(UUID id, UpdateConsumerRequest request) {
        return exchange(
                "更新 Consumer",
                client().post().uri("/admin-api/v1/consumers/{id}/actions/update", id).body(request),
                ConsumerResponse.class
        );
    }

    ConsumerResponse setConsumerEnabled(UUID id, boolean enabled) {
        return exchange(
                enabled ? "启用 Consumer" : "停用 Consumer",
                client().post().uri(
                        "/admin-api/v1/consumers/{id}/actions/{action}",
                        id,
                        enabled ? "enable" : "disable"
                ),
                ConsumerResponse.class
        );
    }

    void deleteConsumer(UUID id) {
        exchangeVoid(
                "删除 Consumer",
                client().post().uri("/admin-api/v1/consumers/{id}/actions/delete", id)
        );
    }

    Optional<ApiKeyResponse> findApiKey(UUID consumerId, String externalId) {
        List<ApiKeyResponse> keys = exchange(
                "查询 API Key",
                client().get().uri(uri -> uri
                        .path("/admin-api/v1/consumers/{consumerId}/api-keys")
                        .queryParam("source", SOURCE)
                        .queryParam("externalId", externalId)
                        .build(consumerId)),
                API_KEY_LIST
        );
        return unique(keys, "API Key");
    }

    ApiKeyDetailResponse getApiKey(UUID consumerId, UUID keyId) {
        return exchange(
                "查询 API Key 详情",
                client().get().uri(
                        "/admin-api/v1/consumers/{consumerId}/api-keys/{keyId}",
                        consumerId,
                        keyId
                ),
                ApiKeyDetailResponse.class
        );
    }

    ApiKeyResponse createApiKey(UUID consumerId, CreateApiKeyRequest request) {
        return exchange(
                "创建 API Key",
                client().post()
                        .uri("/admin-api/v1/consumers/{consumerId}/api-keys", consumerId)
                        .body(request),
                ApiKeyResponse.class
        );
    }

    ApiKeyResponse rotateApiKey(UUID consumerId, UUID keyId, RotateApiKeyRequest request) {
        return exchange(
                "轮换 API Key",
                client().post()
                        .uri(
                                "/admin-api/v1/consumers/{consumerId}/api-keys/{keyId}/actions/rotate",
                                consumerId,
                                keyId
                        )
                        .body(request),
                ApiKeyResponse.class
        );
    }

    void deleteApiKey(UUID consumerId, UUID keyId) {
        exchangeVoid(
                "删除 API Key",
                client().post().uri(
                        "/admin-api/v1/consumers/{consumerId}/api-keys/{keyId}/actions/delete",
                        consumerId,
                        keyId
                )
        );
    }

    Optional<SubscriptionResponse> findSubscription(String externalId) {
        PageResponse<SubscriptionResponse> page = exchange(
                "查询 Subscription",
                client().get().uri(uri -> uri.path("/admin-api/v1/subscriptions")
                        .queryParam("page", 0)
                        .queryParam("size", 2)
                        .queryParam("source", SOURCE)
                        .queryParam("externalId", externalId)
                        .build()),
                SUBSCRIPTION_PAGE
        );
        return unique(page == null ? null : page.content(), "Subscription");
    }

    SubscriptionResponse grantSubscription(GrantSubscriptionRequest request) {
        return exchange(
                "授权 Subscription",
                client().post().uri("/admin-api/v1/subscriptions").body(request),
                SubscriptionResponse.class
        );
    }

    SubscriptionResponse revokeSubscription(UUID id) {
        return exchange(
                "撤回 Subscription",
                client().post().uri("/admin-api/v1/subscriptions/{id}/actions/revoke", id),
                SubscriptionResponse.class
        );
    }

    void deleteSubscription(UUID id) {
        exchangeVoid(
                "删除 Subscription",
                client().post().uri("/admin-api/v1/subscriptions/{id}/actions/delete", id)
        );
    }

    String gatewayUrl(String routePath) {
        String proxyUrl = normalizeHttpUrl(properties.superApiGateway().proxyUrl(), "Proxy URL");
        return proxyUrl + (routePath.startsWith("/") ? routePath : "/" + routePath);
    }

    private RestClient client() {
        ServiceGatewayProperties.SuperApiGateway config = properties.superApiGateway();
        String adminUrl = normalizeHttpUrl(config.adminUrl(), "Admin URL");
        String token = machineToken(config.machineToken());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(config.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(config.requestTimeout());
        return RestClient.builder()
                .baseUrl(adminUrl)
                .defaultHeader(MACHINE_TOKEN_HEADER, token)
                .requestFactory(requestFactory)
                .build();
    }

    private static <T> T exchange(
            String action,
            RestClient.RequestHeadersSpec<?> request,
            Class<T> responseType
    ) {
        try {
            return request.accept(MediaType.APPLICATION_JSON).exchange(
                    (httpRequest, response) -> read(action, response, responseType)
            );
        } catch (SuperApiGatewayAdminException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw unavailable(action, exception);
        }
    }

    private static <T> T exchange(
            String action,
            RestClient.RequestHeadersSpec<?> request,
            ParameterizedTypeReference<T> responseType
    ) {
        try {
            return request.accept(MediaType.APPLICATION_JSON).exchange(
                    (httpRequest, response) -> read(action, response, responseType)
            );
        } catch (SuperApiGatewayAdminException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw unavailable(action, exception);
        }
    }

    private static void exchangeVoid(
            String action,
            RestClient.RequestHeadersSpec<?> request
    ) {
        try {
            request.accept(MediaType.APPLICATION_JSON).exchange((httpRequest, response) -> {
                int status = status(response, action);
                if (status >= 200 && status < 300) {
                    return null;
                }
                throw remoteError(action, response, status);
            });
        } catch (SuperApiGatewayAdminException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw unavailable(action, exception);
        }
    }

    private static <T> T read(
            String action,
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response,
            Class<T> responseType
    ) {
        int status = status(response, action);
        if (status >= 200 && status < 300) {
            return response.bodyTo(responseType);
        }
        throw remoteError(action, response, status);
    }

    private static <T> T read(
            String action,
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response,
            ParameterizedTypeReference<T> responseType
    ) {
        int status = status(response, action);
        if (status >= 200 && status < 300) {
            return response.bodyTo(responseType);
        }
        throw remoteError(action, response, status);
    }

    private static int status(
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response,
            String action
    ) {
        try {
            return response.getStatusCode().value();
        } catch (IOException exception) {
            throw new SuperApiGatewayAdminException(
                    502,
                    null,
                    "Super API Gateway " + action + "失败，无法读取 HTTP 状态"
            );
        }
    }

    private static SuperApiGatewayAdminException remoteError(
            String action,
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response,
            int status
    ) {
        ProblemResponse problem = null;
        try {
            problem = response.bodyTo(ProblemResponse.class);
        } catch (RuntimeException ignored) {
            // Unknown response fields and bodies are not persisted.
        }
        String code = problem == null ? null : problem.code();
        String detail = problem == null ? null : problem.detail();
        String message = "Super API Gateway " + action + "失败（HTTP " + status + "）";
        if (hasText(code)) {
            message += " [" + limit(code, 80) + "]";
        }
        if (hasText(detail)) {
            message += "：" + limit(detail, 300);
        }
        return new SuperApiGatewayAdminException(status, code, message);
    }

    private static SuperApiGatewayAdminException unavailable(String action, RuntimeException exception) {
        String detail = hasText(exception.getMessage()) ? limit(exception.getMessage(), 300) : "远程调用失败";
        return new SuperApiGatewayAdminException(
                502,
                null,
                "Super API Gateway " + action + "请求失败：" + detail
        );
    }

    private static <T> Optional<T> unique(List<T> items, String resource) {
        if (items == null || items.isEmpty()) {
            return Optional.empty();
        }
        if (items.size() > 1) {
            throw new SuperApiGatewayAdminException(
                    409,
                    "EXTERNAL_REFERENCE_NOT_UNIQUE",
                    "Super API Gateway " + resource + " 外部引用返回多个对象"
            );
        }
        return Optional.of(items.getFirst());
    }

    private static String normalizeHttpUrl(String value, String label) {
        String normalized = required(value, label).replaceFirst("/+$", "");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Super API Gateway " + label + " 格式不正确", exception);
        }
        if (uri.getHost() == null
                || !"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())
                || uri.getUserInfo() != null) {
            throw new IllegalArgumentException(
                    "Super API Gateway " + label + " 必须是无用户凭据的 HTTP 或 HTTPS 地址"
            );
        }
        return normalized;
    }

    private static String required(String value, String label) {
        if (!hasText(value)) {
            throw new IllegalArgumentException("Super API Gateway " + label + " 未配置");
        }
        return value.trim();
    }

    private static String machineToken(String value) {
        String token = required(value, "Machine Token");
        if (token.indexOf('\r') >= 0 || token.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Super API Gateway Machine Token 格式不正确");
        }
        return token;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String limit(String value, int maximumLength) {
        return value.substring(0, Math.min(maximumLength, value.length()));
    }
}

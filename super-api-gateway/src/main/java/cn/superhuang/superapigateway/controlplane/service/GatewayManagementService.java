package cn.superhuang.superapigateway.controlplane.service;

import cn.superhuang.superapigateway.controlplane.domain.ApiKeyStatus;
import cn.superhuang.superapigateway.controlplane.domain.GatewayApiKeyEntity;
import cn.superhuang.superapigateway.controlplane.domain.GatewayConsumerEntity;
import cn.superhuang.superapigateway.controlplane.domain.GatewayRouteEntity;
import cn.superhuang.superapigateway.controlplane.domain.GatewayServiceEntity;
import cn.superhuang.superapigateway.controlplane.domain.GatewaySubscriptionEntity;
import cn.superhuang.superapigateway.controlplane.domain.SubscriptionStatus;
import cn.superhuang.superapigateway.configuration.SuperApiGatewayProperties;
import cn.superhuang.superapigateway.controlplane.repository.GatewayApiKeyRepository;
import cn.superhuang.superapigateway.controlplane.repository.GatewayConsumerRepository;
import cn.superhuang.superapigateway.controlplane.repository.GatewayInstanceRepository;
import cn.superhuang.superapigateway.controlplane.repository.GatewayRouteRepository;
import cn.superhuang.superapigateway.controlplane.repository.GatewayServiceRepository;
import cn.superhuang.superapigateway.controlplane.repository.GatewaySubscriptionRepository;
import cn.superhuang.superapigateway.controlplane.web.request.ConsumerRequests;
import cn.superhuang.superapigateway.controlplane.web.request.CreateApiKeyRequest;
import cn.superhuang.superapigateway.controlplane.web.request.GrantSubscriptionRequest;
import cn.superhuang.superapigateway.controlplane.web.request.RotateApiKeyRequest;
import cn.superhuang.superapigateway.controlplane.web.request.RouteRequests;
import cn.superhuang.superapigateway.controlplane.web.request.ServiceRequests;
import cn.superhuang.superapigateway.controlplane.web.response.ApiKeyDetailResponse;
import cn.superhuang.superapigateway.controlplane.web.response.ApiKeyResponse;
import cn.superhuang.superapigateway.controlplane.web.response.ConsumerResponse;
import cn.superhuang.superapigateway.controlplane.web.response.PageResponse;
import cn.superhuang.superapigateway.controlplane.web.response.RouteResponse;
import cn.superhuang.superapigateway.controlplane.web.response.RuntimeResponses;
import cn.superhuang.superapigateway.controlplane.web.response.ServiceResponse;
import cn.superhuang.superapigateway.controlplane.web.response.SubscriptionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.pattern.PathPatternParser;

import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class GatewayManagementService {

    private static final Pattern FIRST_SEGMENT = Pattern.compile("[A-Za-z0-9._~-]+");

    private final GatewayServiceRepository services;
    private final GatewayRouteRepository routes;
    private final GatewayConsumerRepository consumers;
    private final GatewayApiKeyRepository apiKeys;
    private final GatewaySubscriptionRepository subscriptions;
    private final GatewayInstanceRepository instances;
    private final ConfigurationRevisionService revisions;
    private final ApiKeySecretService secrets;
    private final SuperApiGatewayProperties.Runtime runtimeProperties;

    public GatewayManagementService(
            GatewayServiceRepository services,
            GatewayRouteRepository routes,
            GatewayConsumerRepository consumers,
            GatewayApiKeyRepository apiKeys,
            GatewaySubscriptionRepository subscriptions,
            GatewayInstanceRepository instances,
            ConfigurationRevisionService revisions,
            ApiKeySecretService secrets,
            SuperApiGatewayProperties properties
    ) {
        this.services = services;
        this.routes = routes;
        this.consumers = consumers;
        this.apiKeys = apiKeys;
        this.subscriptions = subscriptions;
        this.instances = instances;
        this.revisions = revisions;
        this.secrets = secrets;
        this.runtimeProperties = properties.runtime();
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceResponse> listServices(
            int page,
            int size,
            String source,
            String externalId
    ) {
        ExternalReference reference = externalReference(source, externalId);
        if (reference != null) {
            return singlePage(
                    services.findBySourceAndExternalId(reference.source(), reference.externalId())
                            .map(this::serviceResponse)
            );
        }
        return PageResponse.from(
                services.findAll(page(page, size, "code")),
                this::serviceResponse
        );
    }

    @Transactional(readOnly = true)
    public ServiceResponse getService(UUID id) {
        return serviceResponse(requireService(id));
    }

    @Transactional
    public ServiceResponse createService(ServiceRequests.Create request) {
        String code = request.code().trim();
        if (services.existsByCode(code)) throw new ResourceConflictException("服务 code 已存在");
        String source = source(request.source());
        String externalId = optional(request.externalId());
        validateUpstream(request.upstreamUri());
        GatewayServiceEntity entity = new GatewayServiceEntity(
                code,
                request.name().trim(),
                request.upstreamUri().trim(),
                request.accessMode(),
                request.connectTimeoutMs(),
                request.responseTimeoutMs(),
                request.enabled(),
                optional(request.description()),
                source,
                externalId
        );
        services.save(entity);
        revisions.bump();
        return serviceResponse(entity);
    }

    @Transactional
    public ServiceResponse updateService(UUID id, ServiceRequests.Update request) {
        GatewayServiceEntity entity = requireService(id);
        validateUpstream(request.upstreamUri());
        entity.update(
                request.name().trim(),
                request.upstreamUri().trim(),
                request.accessMode(),
                request.connectTimeoutMs(),
                request.responseTimeoutMs(),
                request.enabled(),
                optional(request.description())
        );
        revisions.bump();
        return serviceResponse(entity);
    }

    @Transactional
    public ServiceResponse setServiceEnabled(UUID id, boolean enabled) {
        GatewayServiceEntity entity = requireService(id);
        entity.setEnabled(enabled);
        revisions.bump();
        return serviceResponse(entity);
    }

    @Transactional
    public void deleteService(UUID id) {
        GatewayServiceEntity entity = requireService(id);
        if (entity.isEnabled()) throw new ResourceConflictException("请先停用服务");
        if (subscriptions.countByServiceIdAndStatus(id, SubscriptionStatus.ACTIVE) > 0) {
            throw new ResourceConflictException("服务存在有效订阅，请先撤回订阅");
        }
        routes.deleteAllByServiceId(id);
        subscriptions.deleteAllByServiceId(id);
        services.delete(entity);
        revisions.bump();
    }

    @Transactional(readOnly = true)
    public List<RouteResponse> listRoutes(
            UUID serviceId,
            String source,
            String externalId,
            String pathPattern
    ) {
        ExternalReference reference = externalReference(source, externalId);
        if (reference != null) {
            return routes.findBySourceAndExternalId(reference.source(), reference.externalId())
                    .filter(route -> serviceId == null || serviceId.equals(route.getServiceId()))
                    .map(route -> List.of(routeResponse(route)))
                    .orElseGet(List::of);
        }
        if (hasText(pathPattern)) {
            return routes.findByPathPattern(pathPattern.trim())
                    .filter(route -> serviceId == null || serviceId.equals(route.getServiceId()))
                    .map(route -> List.of(routeResponse(route)))
                    .orElseGet(List::of);
        }
        List<GatewayRouteEntity> result = serviceId == null
                ? routes.findAll(Sort.by("order", "code"))
                : routes.findAllByServiceIdOrderByOrderAscCodeAsc(serviceId);
        return result.stream().map(this::routeResponse).toList();
    }

    @Transactional(readOnly = true)
    public RouteResponse getRoute(UUID id) {
        return routeResponse(requireRoute(id));
    }

    @Transactional
    public RouteResponse createRoute(RouteRequests.Create request) {
        requireService(request.serviceId());
        if (routes.existsByCode(request.code().trim())) {
            throw new ResourceConflictException("路由 code 已存在");
        }
        String path = validatePath(request.pathPattern());
        if (routes.existsByPathPattern(path)) {
            throw new ResourceConflictException("路径模板已被其他路由使用");
        }
        String upstreamPath = validateUpstreamPath(request.upstreamPath(), request.stripPrefixSegments());
        GatewayRouteEntity entity = new GatewayRouteEntity(
                request.serviceId(),
                request.code().trim(),
                request.name().trim(),
                path,
                request.methods(),
                request.order(),
                request.stripPrefixSegments(),
                upstreamPath,
                request.enabled(),
                source(request.source()),
                optional(request.externalId())
        );
        routes.save(entity);
        revisions.bump();
        return routeResponse(entity);
    }

    @Transactional
    public RouteResponse updateRoute(UUID id, RouteRequests.Update request) {
        GatewayRouteEntity entity = requireRoute(id);
        String path = validatePath(request.pathPattern());
        if (routes.existsByPathPatternAndIdNot(path, id)) {
            throw new ResourceConflictException("路径模板已被其他路由使用");
        }
        String upstreamPath = validateUpstreamPath(request.upstreamPath(), request.stripPrefixSegments());
        entity.update(
                request.name().trim(),
                path,
                request.methods(),
                request.order(),
                request.stripPrefixSegments(),
                upstreamPath,
                request.enabled()
        );
        revisions.bump();
        return routeResponse(entity);
    }

    @Transactional
    public RouteResponse setRouteEnabled(UUID id, boolean enabled) {
        GatewayRouteEntity entity = requireRoute(id);
        entity.setEnabled(enabled);
        revisions.bump();
        return routeResponse(entity);
    }

    @Transactional
    public void deleteRoute(UUID id) {
        routes.delete(requireRoute(id));
        revisions.bump();
    }

    @Transactional(readOnly = true)
    public PageResponse<ConsumerResponse> listConsumers(
            int page,
            int size,
            String source,
            String externalId
    ) {
        ExternalReference reference = externalReference(source, externalId);
        if (reference != null) {
            return singlePage(
                    consumers.findBySourceAndExternalId(reference.source(), reference.externalId())
                            .map(this::consumerResponse)
            );
        }
        return PageResponse.from(consumers.findAll(page(page, size, "code")), this::consumerResponse);
    }

    @Transactional(readOnly = true)
    public ConsumerResponse getConsumer(UUID id) {
        return consumerResponse(requireConsumer(id));
    }

    @Transactional
    public ConsumerResponse createConsumer(ConsumerRequests.Create request) {
        if (consumers.existsByCode(request.code().trim())) {
            throw new ResourceConflictException("Consumer code 已存在");
        }
        GatewayConsumerEntity entity = new GatewayConsumerEntity(
                request.code().trim(),
                request.name().trim(),
                request.enabled(),
                optional(request.description()),
                source(request.source()),
                optional(request.externalId())
        );
        consumers.save(entity);
        revisions.bump();
        return consumerResponse(entity);
    }

    @Transactional
    public ConsumerResponse updateConsumer(UUID id, ConsumerRequests.Update request) {
        GatewayConsumerEntity entity = requireConsumer(id);
        entity.update(request.name().trim(), request.enabled(), optional(request.description()));
        revisions.bump();
        return consumerResponse(entity);
    }

    @Transactional
    public ConsumerResponse setConsumerEnabled(UUID id, boolean enabled) {
        GatewayConsumerEntity entity = requireConsumer(id);
        entity.setEnabled(enabled);
        revisions.bump();
        return consumerResponse(entity);
    }

    @Transactional
    public void deleteConsumer(UUID id) {
        GatewayConsumerEntity entity = requireConsumer(id);
        if (entity.isEnabled()) throw new ResourceConflictException("请先停用 Consumer");
        apiKeys.deleteAllByConsumerId(id);
        subscriptions.deleteAllByConsumerId(id);
        consumers.delete(entity);
        revisions.bump();
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(
            UUID consumerId,
            String source,
            String externalId
    ) {
        requireConsumer(consumerId);
        ExternalReference reference = externalReference(source, externalId);
        if (reference != null) {
            return apiKeys.findByConsumerIdAndSourceAndExternalId(
                            consumerId,
                            reference.source(),
                            reference.externalId()
                    )
                    .map(entity -> List.of(apiKeyResponse(entity, null)))
                    .orElseGet(List::of);
        }
        return apiKeys.findAllByConsumerIdOrderByCreatedAtDesc(consumerId).stream()
                .map(entity -> apiKeyResponse(entity, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public ApiKeyDetailResponse getApiKey(UUID consumerId, UUID keyId) {
        return apiKeyDetailResponse(requireApiKey(consumerId, keyId));
    }

    @Transactional
    public ApiKeyResponse createApiKey(UUID consumerId, CreateApiKeyRequest request) {
        requireConsumer(consumerId);
        if (apiKeys.existsByConsumerIdAndName(consumerId, request.name().trim())) {
            throw new ResourceConflictException("当前 Consumer 已存在同名 API Key");
        }
        boolean supplied = hasText(request.secret());
        ApiKeySecretService.Secret generated = supplied
                ? secrets.describe(request.secret())
                : secrets.generate();
        GatewayApiKeyEntity entity = new GatewayApiKeyEntity(
                consumerId,
                request.name().trim(),
                generated.hash(),
                generated.prefix(),
                generated.lastFour(),
                source(request.source()),
                optional(request.externalId())
        );
        apiKeys.save(entity);
        revisions.bump();
        return apiKeyResponse(entity, supplied ? null : generated.plaintext());
    }

    @Transactional
    public ApiKeyResponse rotateApiKey(
            UUID consumerId,
            UUID keyId,
            RotateApiKeyRequest request
    ) {
        GatewayApiKeyEntity entity = requireApiKey(consumerId, keyId);
        boolean supplied = request != null && hasText(request.secret());
        ApiKeySecretService.Secret generated = supplied
                ? secrets.describe(request.secret())
                : secrets.generate();
        entity.replaceSecret(generated.hash(), generated.prefix(), generated.lastFour());
        revisions.bump();
        return apiKeyResponse(entity, supplied ? null : generated.plaintext());
    }

    @Transactional
    public ApiKeyResponse revokeApiKey(UUID consumerId, UUID keyId) {
        GatewayApiKeyEntity entity = requireApiKey(consumerId, keyId);
        entity.revoke();
        revisions.bump();
        return apiKeyResponse(entity, null);
    }

    @Transactional
    public void deleteApiKey(UUID consumerId, UUID keyId) {
        apiKeys.delete(requireApiKey(consumerId, keyId));
        revisions.bump();
    }

    @Transactional(readOnly = true)
    public PageResponse<SubscriptionResponse> listSubscriptions(
            int page,
            int size,
            UUID consumerId,
            UUID serviceId,
            String source,
            String externalId
    ) {
        ExternalReference reference = externalReference(source, externalId);
        if (reference != null) {
            Optional<GatewaySubscriptionEntity> match = subscriptions
                    .findBySourceAndExternalId(reference.source(), reference.externalId())
                    .filter(entity -> consumerId == null || consumerId.equals(entity.getConsumerId()))
                    .filter(entity -> serviceId == null || serviceId.equals(entity.getServiceId()));
            return singlePage(match.map(this::subscriptionResponse));
        }
        Pageable pageable = page(page, size, "createdAt");
        Page<GatewaySubscriptionEntity> result = consumerId != null
                ? subscriptions.findAllByConsumerId(consumerId, pageable)
                : serviceId != null
                ? subscriptions.findAllByServiceId(serviceId, pageable)
                : subscriptions.findAll(pageable);
        return PageResponse.from(result, this::subscriptionResponse);
    }

    @Transactional
    public SubscriptionResponse grantSubscription(GrantSubscriptionRequest request) {
        requireConsumer(request.consumerId());
        requireService(request.serviceId());
        String requestedSource = source(request.source());
        String requestedExternalId = optional(request.externalId());
        GatewaySubscriptionEntity entity = subscriptions
                .findByConsumerIdAndServiceId(request.consumerId(), request.serviceId())
                .map(existing -> {
                    if (!requestedSource.equals(existing.getSource())
                            || !Objects.equals(requestedExternalId, existing.getExternalId())) {
                        throw new ResourceConflictException(
                                "Consumer 与 Service 的现有订阅属于其他外部引用"
                        );
                    }
                    return existing;
                })
                .orElseGet(() -> new GatewaySubscriptionEntity(
                        request.consumerId(),
                        request.serviceId(),
                        requestedSource,
                        requestedExternalId
                ));
        entity.grant();
        subscriptions.save(entity);
        revisions.bump();
        return subscriptionResponse(entity);
    }

    @Transactional
    public SubscriptionResponse revokeSubscription(UUID id) {
        GatewaySubscriptionEntity entity = subscriptions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("订阅不存在"));
        entity.revoke();
        revisions.bump();
        return subscriptionResponse(entity);
    }

    @Transactional
    public void deleteSubscription(UUID id) {
        GatewaySubscriptionEntity entity = subscriptions.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("订阅不存在"));
        if (entity.getStatus() != SubscriptionStatus.REVOKED) {
            throw new ResourceConflictException("请先撤回订阅");
        }
        subscriptions.delete(entity);
        revisions.bump();
    }

    @Transactional(readOnly = true)
    public RuntimeResponses.Summary runtimeSummary() {
        long revision = revisions.currentRevision();
        Instant offlineBefore = Instant.now().minus(runtimeProperties.instanceStaleAfter());
        List<RuntimeResponses.Instance> nodes = instances.findAll(Sort.by("id")).stream()
                .map(entity -> new RuntimeResponses.Instance(
                        entity.getId(),
                        entity.getLastSeenAt().isBefore(offlineBefore)
                                ? "OFFLINE" : entity.getState(),
                        entity.getLoadedRevision(),
                        entity.getStartedAt(),
                        entity.getLastSeenAt(),
                        entity.getLastError(),
                        entity.getApplicationVersion()
                ))
                .toList();
        return new RuntimeResponses.Summary(
                revision,
                services.count(),
                routes.count(),
                consumers.count(),
                apiKeys.count(),
                subscriptions.count(),
                nodes
        );
    }

    @Transactional
    public long requestReload() {
        return revisions.bump();
    }

    private GatewayServiceEntity requireService(UUID id) {
        return services.findById(id).orElseThrow(() -> new ResourceNotFoundException("服务不存在"));
    }

    private GatewayRouteEntity requireRoute(UUID id) {
        return routes.findById(id).orElseThrow(() -> new ResourceNotFoundException("路由不存在"));
    }

    private GatewayConsumerEntity requireConsumer(UUID id) {
        return consumers.findById(id).orElseThrow(() -> new ResourceNotFoundException("Consumer 不存在"));
    }

    private GatewayApiKeyEntity requireApiKey(UUID consumerId, UUID keyId) {
        GatewayApiKeyEntity entity = apiKeys.findById(keyId)
                .orElseThrow(() -> new ResourceNotFoundException("API Key 不存在"));
        if (!entity.getConsumerId().equals(consumerId)) {
            throw new ResourceNotFoundException("API Key 不存在");
        }
        return entity;
    }

    private ServiceResponse serviceResponse(GatewayServiceEntity entity) {
        return new ServiceResponse(
                entity.getId(),
                entity.getCode(),
                entity.getName(),
                entity.getUpstreamUri(),
                entity.getAccessMode(),
                entity.getConnectTimeoutMs(),
                entity.getResponseTimeoutMs(),
                entity.isEnabled(),
                entity.getDescription(),
                entity.getSource(),
                entity.getExternalId(),
                entity.getRevision(),
                entity.getId() == null ? 0 : routes.countByServiceId(entity.getId()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private RouteResponse routeResponse(GatewayRouteEntity entity) {
        return new RouteResponse(
                entity.getId(), entity.getServiceId(), entity.getCode(), entity.getName(),
                entity.getPathPattern(), entity.getMethods(), entity.getOrder(),
                entity.getStripPrefixSegments(), entity.getUpstreamPath(), entity.isEnabled(), entity.getSource(),
                entity.getExternalId(), entity.getRevision(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private static String validateUpstreamPath(String value, int stripPrefixSegments) {
        String path = optional(value);
        if (path == null) return null;
        if (stripPrefixSegments != 0) {
            throw new IllegalArgumentException("设置上游路径时，去除前缀段数必须为 0");
        }
        if (!path.startsWith("/") || path.contains("//") || path.contains("?") || path.contains("#")) {
            throw new IllegalArgumentException("上游路径必须是以 / 开头的固定路径，且不能包含查询参数或片段");
        }
        return path;
    }

    private ConsumerResponse consumerResponse(GatewayConsumerEntity entity) {
        return new ConsumerResponse(
                entity.getId(), entity.getCode(), entity.getName(), entity.isEnabled(),
                entity.getDescription(), entity.getSource(), entity.getExternalId(), entity.getRevision(),
                entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private ApiKeyResponse apiKeyResponse(GatewayApiKeyEntity entity, String plaintext) {
        return new ApiKeyResponse(
                entity.getId(), entity.getConsumerId(), entity.getName(), entity.getSecretPrefix(),
                entity.getSecretLastFour(), entity.getStatus(), entity.getRotatedAt(), entity.getSource(),
                entity.getExternalId(), plaintext, entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private ApiKeyDetailResponse apiKeyDetailResponse(GatewayApiKeyEntity entity) {
        return new ApiKeyDetailResponse(
                entity.getId(),
                entity.getConsumerId(),
                entity.getName(),
                entity.getSecretPrefix(),
                entity.getSecretLastFour(),
                entity.getStatus(),
                entity.getRotatedAt(),
                entity.getSource(),
                entity.getExternalId(),
                entity.getSecretHash(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    private SubscriptionResponse subscriptionResponse(GatewaySubscriptionEntity entity) {
        return new SubscriptionResponse(
                entity.getId(), entity.getConsumerId(), entity.getServiceId(), entity.getStatus(),
                entity.getSource(), entity.getExternalId(), entity.getCreatedAt(), entity.getUpdatedAt()
        );
    }

    private static Pageable page(int page, int size, String sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(200, Math.max(1, size));
        return PageRequest.of(safePage, safeSize, Sort.by(sort).ascending());
    }

    private static <T> PageResponse<T> singlePage(Optional<T> item) {
        List<T> content = item.map(List::of).orElseGet(List::of);
        return new PageResponse<>(content, 0, 1, content.size(), content.isEmpty() ? 0 : 1);
    }

    private static void validateUpstream(String value) {
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!List.of("http", "https").contains(scheme) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ResourceConflictException("上游地址必须是有效且不含用户凭据的 HTTP/HTTPS URI");
        }
    }

    private static String validatePath(String value) {
        String path = value.trim();
        if (!path.startsWith("/") || path.length() < 2 || path.contains("?") || path.contains("#")
                || path.contains("//") || path.contains(":")) {
            throw new ResourceConflictException("路径模板格式不正确或包含不支持的内联正则");
        }
        String first = path.substring(1).split("/", 2)[0];
        if (!FIRST_SEGMENT.matcher(first).matches()) {
            throw new ResourceConflictException("路径模板首段必须是固定文本");
        }
        try {
            new PathPatternParser().parse(path);
        } catch (RuntimeException exception) {
            throw new ResourceConflictException("路径模板无法解析");
        }
        return path;
    }

    private static String source(String value) {
        String normalized = optional(value);
        return normalized == null ? "MANUAL" : normalized.toUpperCase(Locale.ROOT);
    }

    private static ExternalReference externalReference(String source, String externalId) {
        boolean hasSource = hasText(source);
        boolean hasExternalId = hasText(externalId);
        if (hasSource != hasExternalId) {
            throw new InvalidManagementRequestException(
                    "source 和 externalId 必须同时提供或同时省略"
            );
        }
        if (!hasSource) {
            return null;
        }
        return new ExternalReference(
                source.trim().toUpperCase(Locale.ROOT),
                externalId.trim()
        );
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ExternalReference(String source, String externalId) {
    }
}

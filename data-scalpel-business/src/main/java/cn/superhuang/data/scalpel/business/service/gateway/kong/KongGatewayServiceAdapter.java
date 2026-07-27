package cn.superhuang.data.scalpel.business.service.gateway.kong;

import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceInspectionSpec;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceOperationException;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServicePort;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceReference;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceResult;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceSpec;
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
import java.util.Map;

/** Kong Admin API implementation of the provider-neutral data-service publication capability. */
@Component
public class KongGatewayServiceAdapter implements GatewayServicePort {

    private static final String MANAGED_TAG = "datascalpel";
    private static final String SERVICE_TAG = "datascalpel-service";

    private final ServiceGatewayProperties properties;

    public KongGatewayServiceAdapter(ServiceGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public GatewayProvider provider() {
        return GatewayProvider.KONG;
    }

    @Override
    public GatewayServiceResult publish(GatewayServiceSpec service) {
        try {
            RestClient client = client();
            KongServiceResponse kongService = upsertService(client, service);
            if (service.accessMode() == DataServiceAccessMode.SUBSCRIPTION_REQUIRED) {
                removeExistingRouteBeforeProtection(client, service, kongService);
                KongPluginResponse keyAuth = upsertPlugin(
                        client,
                        service,
                        kongService,
                        "key-auth",
                        new KongKeyAuthPluginConfig(
                                List.of("X-API-Key"),
                                true,
                                true,
                                false,
                                false,
                                true
                        )
                );
                KongPluginResponse acl = upsertPlugin(
                        client,
                        service,
                        kongService,
                        "acl",
                        new KongAclPluginConfig(
                                List.of(KongGatewayManagedNames.serviceAclGroup(service.id())),
                                List.of(),
                                true,
                                false
                        )
                );
                verifyProtection(service, kongService, keyAuth, acl);
            } else {
                removeManagedAccessPlugins(client, service, kongService);
            }
            KongRouteResponse kongRoute = upsertRoute(client, service, kongService);
            verifySynchronized(service, kongService, kongRoute);
            return new GatewayServiceResult(
                    kongService.id(),
                    kongRoute.id(),
                    gatewayUrl(service.routePath())
            );
        } catch (GatewayServiceOperationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GatewayServiceOperationException(
                    "Kong 服务发布请求失败：" + safeCauseMessage(exception),
                    exception
            );
        }
    }

    @Override
    public void remove(GatewayServiceReference service) {
        try {
            RestClient client = client();
            KongRouteResponse route = findRoute(
                    client,
                    hasText(service.externalRouteId()) ? service.externalRouteId() : routeName(service.id())
            );
            if (route != null) {
                verifyOwnership(service.id(), route.tags(), "Route");
                delete(client, "/routes/{route}", route.id(), "Route");
            }

            KongServiceResponse kongService = findService(
                    client,
                    hasText(service.externalServiceId()) ? service.externalServiceId() : serviceName(service.id())
            );
            if (kongService != null) {
                verifyOwnership(service.id(), kongService.tags(), "Service");
                delete(client, "/services/{service}", kongService.id(), "Service");
            }
        } catch (GatewayServiceOperationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GatewayServiceOperationException(
                    "Kong 服务撤回请求失败：" + safeCauseMessage(exception),
                    exception
            );
        }
    }

    @Override
    public GatewayInspectionResult inspect(GatewayServiceInspectionSpec inspection) {
        try {
            RestClient client = client();
            GatewayServiceSpec expected = inspection.expected();
            GatewayServiceReference reference = inspection.reference();
            KongServiceResponse service = findServiceForInspection(
                    client, expected.id(), reference.externalServiceId()
            );
            KongRouteResponse route = findRouteForInspection(
                    client, expected.id(), reference.externalRouteId()
            );

            if (!inspection.expectedPresent()) {
                if (service == null && route == null) {
                    return GatewayInspectionResult.inSync(
                            "Kong Service 和 Route 已不存在，符合本地撤回期望"
                    );
                }
                if (service != null && !isOwnedEntity(expected.id(), service.tags())
                        || route != null && !isOwnedEntity(expected.id(), route.tags())) {
                    return GatewayInspectionResult.drifted(
                            GatewayReconciliationReason.OWNER_MISMATCH,
                            "绑定 ID 或确定性名称指向了不属于当前数据服务的 Kong 对象"
                    );
                }
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.UNEXPECTED_REMOTE,
                        "本地期望撤回，但 Kong Service 或 Route 仍然存在"
                );
            }

            if (service == null || route == null) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.REMOTE_MISSING,
                        service == null && route == null
                                ? "Kong Service 和 Route 均不存在"
                                : service == null ? "Kong Service 不存在" : "Kong Route 不存在"
                );
            }
            if (!isOwnedEntity(expected.id(), service.tags())
                    || !isOwnedEntity(expected.id(), route.tags())
                    || route.service() == null
                    || !service.id().equals(route.service().id())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.OWNER_MISMATCH,
                        "Kong Service、Route 的归属或关联关系与当前数据服务不一致"
                );
            }
            if (!serviceName(expected.id()).equals(service.name())
                    || !matchesUpstreamUrl(expected.upstreamUrl(), service)
                    || !routeName(expected.id()).equals(route.name())
                    || route.paths() == null
                    || !route.paths().equals(List.of(expected.routePath()))
                    || route.methods() == null
                    || !route.methods().equals(List.of("POST"))
                    || route.protocols() == null
                    || !route.protocols().containsAll(List.of("http", "https"))
                    || route.stripPath()
                    || route.preserveHost()
                    || hasText(reference.externalServiceId())
                    && !reference.externalServiceId().equals(service.id())
                    || hasText(reference.externalRouteId())
                    && !reference.externalRouteId().equals(route.id())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.CONFIG_MISMATCH,
                        "Kong Service 或 Route 配置与本地发布期望不一致"
                );
            }

            GatewayInspectionResult protection = inspectProtection(client, expected, service);
            return protection == null
                    ? GatewayInspectionResult.inSync("Kong Service、Route 和访问控制配置与本地期望一致")
                    : protection;
        } catch (GatewayServiceOperationException exception) {
            throw exception;
        } catch (RestClientException | IllegalArgumentException exception) {
            throw new GatewayServiceOperationException(
                    "Kong 服务状态检查失败：" + safeCauseMessage(exception),
                    exception
            );
        }
    }

    private GatewayInspectionResult inspectProtection(
            RestClient client,
            GatewayServiceSpec expected,
            KongServiceResponse service
    ) {
        KongPluginResponse keyAuth = findPlugin(client, service.id(), "key-auth");
        KongPluginResponse acl = findPlugin(client, service.id(), "acl");
        if (expected.accessMode() == DataServiceAccessMode.PUBLIC) {
            if (keyAuth == null && acl == null) return null;
            if (keyAuth != null && !isManagedAccessPlugin(
                    expected.id(), service.id(), "key-auth", keyAuth
            ) || acl != null && !isManagedAccessPlugin(
                    expected.id(), service.id(), "acl", acl
            )) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.OWNER_MISMATCH,
                        "公开服务存在非 DataScalpel 管理的 Kong 访问控制插件"
                );
            }
            return GatewayInspectionResult.drifted(
                    GatewayReconciliationReason.CONFIG_MISMATCH,
                    "公开服务仍存在 Kong key-auth 或 acl 插件"
            );
        }
        if (keyAuth == null || acl == null) {
            return GatewayInspectionResult.drifted(
                    GatewayReconciliationReason.CONFIG_MISMATCH,
                    "订阅访问服务缺少 Kong key-auth 或 acl 插件"
            );
        }
        if (!isManagedAccessPlugin(expected.id(), service.id(), "key-auth", keyAuth)
                || !isManagedAccessPlugin(expected.id(), service.id(), "acl", acl)) {
            return GatewayInspectionResult.drifted(
                    GatewayReconciliationReason.OWNER_MISMATCH,
                    "Kong 访问控制插件归属与当前数据服务不一致"
            );
        }
        if (!containsBoolean(keyAuth.config(), "hide_credentials", true)
                || !containsBoolean(keyAuth.config(), "key_in_header", true)
                || !containsBoolean(keyAuth.config(), "key_in_query", false)
                || !containsBoolean(keyAuth.config(), "key_in_body", false)
                || !containsBoolean(keyAuth.config(), "run_on_preflight", true)
                || !containsList(keyAuth.config(), "key_names", List.of("X-API-Key"))
                || !containsBoolean(acl.config(), "hide_groups_header", true)
                || !containsBoolean(acl.config(), "always_use_authenticated_groups", false)
                || !containsList(
                        acl.config(),
                        "allow",
                        List.of(KongGatewayManagedNames.serviceAclGroup(expected.id()))
                )) {
            return GatewayInspectionResult.drifted(
                    GatewayReconciliationReason.CONFIG_MISMATCH,
                    "Kong key-auth 或 acl 插件配置与本地发布期望不一致"
            );
        }
        return null;
    }

    private static KongServiceResponse findServiceForInspection(
            RestClient client,
            java.util.UUID id,
            String externalId
    ) {
        KongServiceResponse service = hasText(externalId) ? findService(client, externalId) : null;
        return service != null ? service : findService(client, serviceName(id));
    }

    private static KongRouteResponse findRouteForInspection(
            RestClient client,
            java.util.UUID id,
            String externalId
    ) {
        KongRouteResponse route = hasText(externalId) ? findRoute(client, externalId) : null;
        return route != null ? route : findRoute(client, routeName(id));
    }

    private KongServiceResponse upsertService(RestClient client, GatewayServiceSpec service) {
        KongServiceResponse existing = findService(client, serviceName(service.id()));
        KongServiceRequest request = serviceRequest(service, existing == null ? List.of() : existing.tags());
        if (existing == null) {
            KongServiceResponse created = createService(client, request);
            if (created != null) {
                return created;
            }
            existing = findService(client, serviceName(service.id()));
            if (existing == null) {
                throw new GatewayServiceOperationException("Kong Service 创建冲突后仍无法读取");
            }
        }
        verifyOwnership(service.id(), existing.tags(), "Service");
        KongServiceResponse updated = patchService(client, existing.id(), request);
        if (updated != null) {
            return updated;
        }
        KongServiceResponse recreated = createService(client, request);
        if (recreated != null) {
            return recreated;
        }
        KongServiceResponse reloaded = findService(client, serviceName(service.id()));
        if (reloaded == null) {
            throw new GatewayServiceOperationException("Kong Service 更新后无法读取");
        }
        verifyOwnership(service.id(), reloaded.tags(), "Service");
        return reloaded;
    }

    private KongRouteResponse upsertRoute(
            RestClient client,
            GatewayServiceSpec service,
            KongServiceResponse kongService
    ) {
        KongRouteResponse existing = findRoute(client, routeName(service.id()));
        KongRouteRequest request = routeRequest(service, existing == null ? List.of() : existing.tags());
        if (existing == null) {
            KongRouteResponse created = createRoute(client, kongService.id(), request);
            if (created != null) {
                return created;
            }
            existing = findRoute(client, routeName(service.id()));
            if (existing == null) {
                throw new GatewayServiceOperationException("Kong Route 创建冲突后仍无法读取");
            }
        }
        verifyOwnership(service.id(), existing.tags(), "Route");
        verifyRouteService(kongService.id(), existing);
        KongRouteResponse updated = patchRoute(client, existing.id(), request);
        if (updated != null) {
            return updated;
        }
        KongRouteResponse recreated = createRoute(client, kongService.id(), request);
        if (recreated != null) {
            return recreated;
        }
        KongRouteResponse reloaded = findRoute(client, routeName(service.id()));
        if (reloaded == null) {
            throw new GatewayServiceOperationException("Kong Route 更新后无法读取");
        }
        verifyOwnership(service.id(), reloaded.tags(), "Route");
        verifyRouteService(kongService.id(), reloaded);
        return reloaded;
    }

    private void removeExistingRouteBeforeProtection(
            RestClient client,
            GatewayServiceSpec service,
            KongServiceResponse kongService
    ) {
        KongRouteResponse existing = findRoute(client, routeName(service.id()));
        if (existing == null) return;
        verifyOwnership(service.id(), existing.tags(), "Route");
        verifyRouteService(kongService.id(), existing);
        delete(client, "/routes/{route}", existing.id(), "Route");
    }

    private void removeManagedAccessPlugins(
            RestClient client,
            GatewayServiceSpec service,
            KongServiceResponse kongService
    ) {
        KongPluginResponse keyAuth = findPlugin(client, kongService.id(), "key-auth");
        KongPluginResponse acl = findPlugin(client, kongService.id(), "acl");
        boolean removeKeyAuth = isManagedAccessPlugin(service.id(), kongService.id(), "key-auth", keyAuth);
        boolean removeAcl = isManagedAccessPlugin(service.id(), kongService.id(), "acl", acl);
        if (!removeKeyAuth && !removeAcl) return;

        removeExistingRouteBeforeProtection(client, service, kongService);
        if (removeAcl) delete(client, "/plugins/{plugin}", acl.id(), "acl 插件");
        if (removeKeyAuth) delete(client, "/plugins/{plugin}", keyAuth.id(), "key-auth 插件");
    }

    private KongPluginResponse upsertPlugin(
            RestClient client,
            GatewayServiceSpec service,
            KongServiceResponse kongService,
            String pluginName,
            Object config
    ) {
        KongPluginResponse existing = findPlugin(client, kongService.id(), pluginName);
        KongPluginRequest request = new KongPluginRequest(
                pluginName,
                config,
                managedAccessTags(service.id(), existing == null ? List.of() : existing.tags())
        );
        if (existing == null) {
            KongPluginResponse created = createPlugin(client, kongService.id(), request);
            if (created != null) return created;
            existing = findPlugin(client, kongService.id(), pluginName);
            if (existing == null) {
                throw new GatewayServiceOperationException(
                        "Kong " + pluginName + " 插件创建冲突后仍无法读取"
                );
            }
        }
        verifyPluginOwnership(service.id(), kongService.id(), pluginName, existing);
        KongPluginResponse updated = patchPlugin(client, existing.id(), request);
        if (updated != null) return updated;
        KongPluginResponse recreated = createPlugin(client, kongService.id(), request);
        if (recreated != null) return recreated;
        KongPluginResponse reloaded = findPlugin(client, kongService.id(), pluginName);
        if (reloaded == null) {
            throw new GatewayServiceOperationException("Kong " + pluginName + " 插件更新后无法读取");
        }
        verifyPluginOwnership(service.id(), kongService.id(), pluginName, reloaded);
        return reloaded;
    }

    private KongServiceRequest serviceRequest(
            GatewayServiceSpec service,
            List<String> existingTags
    ) {
        return new KongServiceRequest(
                serviceName(service.id()),
                normalizeHttpUrl(service.upstreamUrl(), "Service Engine 公共地址"),
                managedTags(service.id(), existingTags)
        );
    }

    private KongRouteRequest routeRequest(
            GatewayServiceSpec service,
            List<String> existingTags
    ) {
        return new KongRouteRequest(
                routeName(service.id()),
                List.of(service.routePath()),
                List.of("POST"),
                List.of("http", "https"),
                false,
                false,
                managedTags(service.id(), existingTags)
        );
    }

    private static List<String> managedTags(java.util.UUID id, List<String> existingTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (existingTags != null) {
            tags.addAll(existingTags);
        }
        tags.add(MANAGED_TAG);
        tags.add(SERVICE_TAG);
        tags.add(ownerTag(id));
        return List.copyOf(tags);
    }

    private static List<String> managedAccessTags(java.util.UUID id, List<String> existingTags) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        if (existingTags != null) tags.addAll(existingTags);
        tags.add(MANAGED_TAG);
        tags.add("datascalpel-service-access");
        tags.add(ownerTag(id));
        return List.copyOf(tags);
    }

    private static void verifyOwnership(java.util.UUID id, List<String> tags, String entity) {
        if (tags == null || !tags.contains(MANAGED_TAG)
                || !tags.contains(SERVICE_TAG) || !tags.contains(ownerTag(id))) {
            throw new GatewayServiceOperationException(
                    "Kong " + entity + " 归属校验失败，拒绝接管或删除"
            );
        }
    }

    private static boolean isOwnedEntity(java.util.UUID id, List<String> tags) {
        return tags != null
                && tags.contains(MANAGED_TAG)
                && tags.contains(SERVICE_TAG)
                && tags.contains(ownerTag(id));
    }

    private static void verifyRouteService(String serviceId, KongRouteResponse route) {
        if (route.service() == null || !serviceId.equals(route.service().id())) {
            throw new GatewayServiceOperationException("Kong Route 已绑定其他 Service，拒绝接管");
        }
    }

    private static void verifyPluginOwnership(
            java.util.UUID id,
            String serviceId,
            String pluginName,
            KongPluginResponse plugin
    ) {
        if (plugin == null || !hasText(plugin.id())
                || !pluginName.equals(plugin.name())
                || plugin.service() == null || !serviceId.equals(plugin.service().id())
                || plugin.tags() == null
                || !plugin.tags().contains(MANAGED_TAG)
                || !plugin.tags().contains("datascalpel-service-access")
                || !plugin.tags().contains(ownerTag(id))) {
            throw new GatewayServiceOperationException(
                    "Kong " + pluginName + " 插件归属校验失败，拒绝接管"
            );
        }
    }

    private static boolean isManagedAccessPlugin(
            java.util.UUID id,
            String serviceId,
            String pluginName,
            KongPluginResponse plugin
    ) {
        return plugin != null
                && hasText(plugin.id())
                && pluginName.equals(plugin.name())
                && plugin.service() != null
                && serviceId.equals(plugin.service().id())
                && plugin.tags() != null
                && plugin.tags().contains(MANAGED_TAG)
                && plugin.tags().contains("datascalpel-service-access")
                && plugin.tags().contains(ownerTag(id));
    }

    private static void verifyProtection(
            GatewayServiceSpec expected,
            KongServiceResponse service,
            KongPluginResponse keyAuth,
            KongPluginResponse acl
    ) {
        verifyPluginOwnership(expected.id(), service.id(), "key-auth", keyAuth);
        verifyPluginOwnership(expected.id(), service.id(), "acl", acl);
        if (!containsBoolean(keyAuth.config(), "hide_credentials", true)
                || !containsBoolean(keyAuth.config(), "key_in_header", true)
                || !containsBoolean(keyAuth.config(), "key_in_query", false)
                || !containsBoolean(keyAuth.config(), "key_in_body", false)
                || !containsList(keyAuth.config(), "key_names", List.of("X-API-Key"))) {
            throw new GatewayServiceOperationException("Kong key-auth 插件配置未生效");
        }
        if (!containsBoolean(acl.config(), "hide_groups_header", true)
                || !containsList(
                        acl.config(),
                        "allow",
                        List.of(KongGatewayManagedNames.serviceAclGroup(expected.id()))
                )) {
            throw new GatewayServiceOperationException("Kong acl 插件配置未生效");
        }
    }

    private static boolean containsBoolean(Map<String, Object> config, String key, boolean value) {
        return config != null && Boolean.valueOf(value).equals(config.get(key));
    }

    private static boolean containsList(Map<String, Object> config, String key, List<String> values) {
        if (config == null || !(config.get(key) instanceof List<?> actual)) return false;
        return actual.equals(values);
    }

    private static void verifySynchronized(
            GatewayServiceSpec expected,
            KongServiceResponse service,
            KongRouteResponse route
    ) {
        if (service == null || !hasText(service.id())
                || !serviceName(expected.id()).equals(service.name())) {
            throw new GatewayServiceOperationException("Kong 未返回匹配的 Service 发布结果");
        }
        verifyOwnership(expected.id(), service.tags(), "Service");
        if (route == null || !hasText(route.id())
                || !routeName(expected.id()).equals(route.name())
                || route.paths() == null || !route.paths().equals(List.of(expected.routePath()))
                || route.methods() == null || !route.methods().contains("POST")
                || route.stripPath()) {
            throw new GatewayServiceOperationException("Kong 未返回匹配的 Route 发布结果");
        }
        verifyOwnership(expected.id(), route.tags(), "Route");
        verifyRouteService(service.id(), route);
    }

    private static KongServiceResponse findService(RestClient client, String service) {
        return client.get()
                .uri("/services/{service}", service)
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        return null;
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw error("查询 Service", response);
                    }
                    return response.bodyTo(KongServiceResponse.class);
                });
    }

    private static KongServiceResponse createService(RestClient client, KongServiceRequest request) {
        return client.post()
                .uri("/services")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().value() == 409) {
                        return null;
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw error("创建 Service", response);
                    }
                    return response.bodyTo(KongServiceResponse.class);
                });
    }

    private static KongServiceResponse patchService(
            RestClient client,
            String externalId,
            KongServiceRequest request
    ) {
        return client.patch()
                .uri("/services/{service}", externalId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        return null;
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw error("更新 Service", response);
                    }
                    return response.bodyTo(KongServiceResponse.class);
                });
    }

    private static KongRouteResponse findRoute(RestClient client, String route) {
        return client.get()
                .uri("/routes/{route}", route)
                .accept(MediaType.APPLICATION_JSON)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        return null;
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw error("查询 Route", response);
                    }
                    return response.bodyTo(KongRouteResponse.class);
                });
    }

    private static KongRouteResponse createRoute(
            RestClient client,
            String serviceId,
            KongRouteRequest request
    ) {
        return client.post()
                .uri("/services/{service}/routes", serviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().value() == 409) {
                        return null;
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw error("创建 Route", response);
                    }
                    return response.bodyTo(KongRouteResponse.class);
                });
    }

    private static KongRouteResponse patchRoute(
            RestClient client,
            String externalId,
            KongRouteRequest request
    ) {
        return client.patch()
                .uri("/routes/{route}", externalId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().value() == 404) {
                        return null;
                    }
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw error("更新 Route", response);
                    }
                    return response.bodyTo(KongRouteResponse.class);
                });
    }

    private static KongPluginResponse findPlugin(
            RestClient client,
            String serviceId,
            String pluginName
    ) {
        KongPluginListResponse response = client.get()
                .uri("/services/{service}/plugins?name={name}", serviceId, pluginName)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(KongPluginListResponse.class);
        if (response == null || response.data() == null || response.data().isEmpty()) return null;
        return response.data().stream()
                .filter(plugin -> pluginName.equals(plugin.name()))
                .filter(plugin -> plugin.service() != null && serviceId.equals(plugin.service().id()))
                .findFirst()
                .orElse(null);
    }

    private static KongPluginResponse createPlugin(
            RestClient client,
            String serviceId,
            KongPluginRequest request
    ) {
        return client.post()
                .uri("/services/{service}/plugins", serviceId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().value() == 409) return null;
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw error("创建 " + request.name() + " 插件", response);
                    }
                    return response.bodyTo(KongPluginResponse.class);
                });
    }

    private static KongPluginResponse patchPlugin(
            RestClient client,
            String externalId,
            KongPluginRequest request
    ) {
        return client.patch()
                .uri("/plugins/{plugin}", externalId)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((httpRequest, response) -> {
                    if (response.getStatusCode().value() == 404) return null;
                    if (!response.getStatusCode().is2xxSuccessful()) {
                        throw error("更新 " + request.name() + " 插件", response);
                    }
                    return response.bodyTo(KongPluginResponse.class);
                });
    }

    private static void delete(RestClient client, String path, String id, String entity) {
        client.delete()
                .uri(path, id)
                .exchange((request, response) -> {
                    if (response.getStatusCode().value() == 404
                            || response.getStatusCode().is2xxSuccessful()) {
                        return null;
                    }
                    throw error("删除 " + entity, response);
                });
    }

    private RestClient client() {
        ServiceGatewayProperties.Kong kong = properties.kong();
        String adminUrl = normalizeHttpUrl(kong.adminUrl(), "Kong Admin URL");
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

    private String gatewayUrl(String routePath) {
        String proxyUrl = normalizeHttpUrl(properties.kong().proxyUrl(), "Kong Proxy URL");
        return proxyUrl + routePath;
    }

    private static boolean matchesUpstreamUrl(String expectedUrl, KongServiceResponse actual) {
        URI expected = URI.create(normalizeHttpUrl(expectedUrl, "Service Engine 公共地址"));
        if (hasText(actual.url())) {
            URI reported = URI.create(normalizeHttpUrl(actual.url(), "Kong Service 上游地址"));
            return sameUpstream(expected, reported);
        }
        if (!hasText(actual.protocol()) || !hasText(actual.host())) {
            return false;
        }
        return expected.getScheme().equalsIgnoreCase(actual.protocol())
                && expected.getHost().equalsIgnoreCase(actual.host())
                && effectivePort(expected.getScheme(), expected.getPort())
                == effectivePort(actual.protocol(), actual.port() == null ? -1 : actual.port())
                && normalizedPath(expected.getRawPath()).equals(normalizedPath(actual.path()));
    }

    private static boolean sameUpstream(URI expected, URI actual) {
        return expected.getScheme().equalsIgnoreCase(actual.getScheme())
                && expected.getHost().equalsIgnoreCase(actual.getHost())
                && effectivePort(expected.getScheme(), expected.getPort())
                == effectivePort(actual.getScheme(), actual.getPort())
                && normalizedPath(expected.getRawPath()).equals(normalizedPath(actual.getRawPath()));
    }

    private static int effectivePort(String protocol, int port) {
        if (port >= 0) return port;
        return "https".equalsIgnoreCase(protocol) ? 443 : 80;
    }

    private static String normalizedPath(String path) {
        if (!hasText(path) || "/".equals(path)) return "";
        return path.replaceFirst("/+$", "");
    }

    private static String normalizeHttpUrl(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new GatewayServiceOperationException(label + " 未配置");
        }
        String normalized = value.trim().replaceFirst("/+$", "");
        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new GatewayServiceOperationException(label + " 格式不正确", exception);
        }
        if (uri.getHost() == null
                || !"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new GatewayServiceOperationException(label + " 必须是 HTTP 或 HTTPS 地址");
        }
        return normalized;
    }

    private static GatewayServiceOperationException error(
            String action,
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response
    ) {
        int status;
        try {
            status = response.getStatusCode().value();
        } catch (IOException exception) {
            return new GatewayServiceOperationException(
                    "Kong 服务" + action + "失败，无法读取 HTTP 状态",
                    exception
            );
        }
        String detail = null;
        try {
            KongServiceErrorResponse error = response.bodyTo(KongServiceErrorResponse.class);
            if (error != null) {
                detail = hasText(error.message()) ? error.message() : error.name();
            }
        } catch (RuntimeException ignored) {
            // Keep unknown external response fields out of persisted errors.
        }
        String suffix = hasText(detail) ? "：" + limit(detail, 300) : "";
        return new GatewayServiceOperationException(
                "Kong 服务" + action + "失败（HTTP " + status + "）" + suffix
        );
    }

    private static String serviceName(java.util.UUID id) {
        return "datascalpel-service-" + id;
    }

    private static String routeName(java.util.UUID id) {
        return "datascalpel-route-" + id;
    }

    private static String ownerTag(java.util.UUID id) {
        return "datascalpel-data-service-" + id;
    }

    private static String safeCauseMessage(Exception exception) {
        String message = exception.getMessage();
        return hasText(message) ? limit(message, 300) : "远程调用失败";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String limit(String value, int maximumLength) {
        return value.substring(0, Math.min(maximumLength, value.length()));
    }
}

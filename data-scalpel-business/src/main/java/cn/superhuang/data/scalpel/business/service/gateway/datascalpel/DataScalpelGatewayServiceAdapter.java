package cn.superhuang.data.scalpel.business.service.gateway.datascalpel;

import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.ServiceGatewayProperties;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceInspectionSpec;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceOperationException;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServicePort;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceReference;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceResult;
import cn.superhuang.data.scalpel.business.service.gateway.service.GatewayServiceSpec;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayAdapterSupport.equalText;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayAdapterSupport.milliseconds;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayAdapterSupport.owned;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.CreateRouteRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.CreateServiceRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.RouteResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.ServiceResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.UpdateRouteRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.UpdateServiceRequest;

@Component
public class DataScalpelGatewayServiceAdapter implements GatewayServicePort {

    private static final int ROUTE_ORDER = 0;
    private static final int STRIP_PREFIX_SEGMENTS = 0;
    private static final Set<String> METHODS = Set.of("POST");

    private final SuperApiGatewayAdminClient client;
    private final ServiceGatewayProperties properties;

    public DataScalpelGatewayServiceAdapter(
            SuperApiGatewayAdminClient client,
            ServiceGatewayProperties properties
    ) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public GatewayProvider provider() {
        return GatewayProvider.DATASCALPEL;
    }

    @Override
    public GatewayServiceResult publish(GatewayServiceSpec service) {
        try {
            String externalId = service.id().toString();
            Optional<RouteResponse> existingRoute = client.findRoute(externalId);
            if (existingRoute.isPresent()) {
                requireRouteOwnership(service.id(), existingRoute.get());
                if (existingRoute.get().enabled()) {
                    client.setRouteEnabled(existingRoute.get().id(), false);
                }
            }

            ServiceResponse gatewayService = upsertDisabledService(service);
            RouteResponse gatewayRoute = upsertDisabledRoute(service, gatewayService, existingRoute);
            client.setServiceEnabled(gatewayService.id(), true);
            client.setRouteEnabled(gatewayRoute.id(), true);

            ServiceResponse verifiedService = client.findService(externalId)
                    .orElseThrow(() -> new GatewayServiceOperationException(
                            "Super API Gateway Service 发布后无法读取"
                    ));
            RouteResponse verifiedRoute = client.findRoute(externalId)
                    .orElseThrow(() -> new GatewayServiceOperationException(
                            "Super API Gateway Route 发布后无法读取"
                    ));
            GatewayInspectionResult verification = inspectPresent(service, null, verifiedService, verifiedRoute);
            if (verification.reason() != null) {
                throw new GatewayServiceOperationException(verification.message());
            }
            return new GatewayServiceResult(
                    verifiedService.id().toString(),
                    verifiedRoute.id().toString(),
                    client.gatewayUrl(service.routePath())
            );
        } catch (GatewayServiceOperationException exception) {
            throw exception;
        } catch (SuperApiGatewayAdminException | IllegalArgumentException exception) {
            throw operationFailure("服务发布", exception);
        }
    }

    @Override
    public void remove(GatewayServiceReference service) {
        try {
            Optional<RouteResponse> route = client.findRoute(service.id().toString());
            if (route.isPresent()) {
                requireRouteOwnership(service.id(), route.get());
                if (route.get().enabled()) {
                    client.setRouteEnabled(route.get().id(), false);
                }
                deleteRoute(route.get().id());
            }

            Optional<ServiceResponse> gatewayService = client.findService(service.id().toString());
            if (gatewayService.isEmpty()) {
                return;
            }
            requireServiceOwnership(service.id(), gatewayService.get());
            ServiceResponse disabled = gatewayService.get().enabled()
                    ? client.setServiceEnabled(gatewayService.get().id(), false)
                    : gatewayService.get();
            try {
                client.deleteService(disabled.id());
            } catch (SuperApiGatewayAdminException exception) {
                if (!exception.isConflict()) {
                    throw exception;
                }
                // Active subscriptions retain this disabled Service as their stable identity anchor.
            }
        } catch (GatewayServiceOperationException exception) {
            throw exception;
        } catch (SuperApiGatewayAdminException | IllegalArgumentException exception) {
            throw operationFailure("服务撤回", exception);
        }
    }

    @Override
    public GatewayInspectionResult inspect(GatewayServiceInspectionSpec inspection) {
        try {
            GatewayServiceSpec expected = inspection.expected();
            Optional<ServiceResponse> service = client.findService(expected.id().toString());
            Optional<RouteResponse> route = client.findRoute(expected.id().toString());

            if (!inspection.expectedPresent()) {
                if (service.isPresent() && !owned(service.get().source(), service.get().externalId(), expected.id())
                        || route.isPresent() && !owned(
                        route.get().source(), route.get().externalId(), expected.id()
                )) {
                    return GatewayInspectionResult.drifted(
                            GatewayReconciliationReason.OWNER_MISMATCH,
                            "绑定或外部引用指向不属于当前数据服务的 Super API Gateway 对象"
                    );
                }
                boolean serviceInactive = service.isEmpty() || !service.get().enabled();
                boolean routeInactive = route.isEmpty() || !route.get().enabled();
                return serviceInactive && routeInactive
                        ? GatewayInspectionResult.inSync(
                        "Super API Gateway Route 不可访问且 Service 未启用，符合撤回期望"
                )
                        : GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.UNEXPECTED_REMOTE,
                        "本地期望撤回，但 Super API Gateway Service 或 Route 仍然启用"
                );
            }

            if (service.isEmpty() || route.isEmpty()) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.REMOTE_MISSING,
                        service.isEmpty() && route.isEmpty()
                                ? "Super API Gateway Service 和 Route 均不存在"
                                : service.isEmpty() ? "Super API Gateway Service 不存在"
                                : "Super API Gateway Route 不存在"
                );
            }
            return inspectPresent(expected, inspection.reference(), service.get(), route.get());
        } catch (SuperApiGatewayAdminException | IllegalArgumentException exception) {
            throw operationFailure("服务状态检查", exception);
        }
    }

    private ServiceResponse upsertDisabledService(GatewayServiceSpec service) {
        Optional<ServiceResponse> existing = client.findService(service.id().toString());
        if (existing.isPresent()) {
            requireServiceOwnership(service.id(), existing.get());
            requireServiceCode(service, existing.get());
            return client.updateService(existing.get().id(), updateServiceRequest(service, false));
        }
        try {
            return client.createService(new CreateServiceRequest(
                    service.code(),
                    service.name(),
                    service.upstreamUrl(),
                    service.accessMode().name(),
                    upstreamConnectTimeoutMs(),
                    upstreamResponseTimeoutMs(),
                    false,
                    description(service),
                    SuperApiGatewayAdminClient.SOURCE,
                    service.id().toString()
            ));
        } catch (SuperApiGatewayAdminException exception) {
            if (!exception.isConflict()) {
                throw exception;
            }
            ServiceResponse concurrent = client.findService(service.id().toString())
                    .orElseThrow(() -> new GatewayServiceOperationException(
                            "Super API Gateway 中存在冲突的 Service，拒绝接管"
                    ));
            requireServiceOwnership(service.id(), concurrent);
            requireServiceCode(service, concurrent);
            return client.updateService(concurrent.id(), updateServiceRequest(service, false));
        }
    }

    private RouteResponse upsertDisabledRoute(
            GatewayServiceSpec service,
            ServiceResponse gatewayService,
            Optional<RouteResponse> current
    ) {
        if (current.isPresent()) {
            RouteResponse existing = current.get();
            requireRouteOwnership(service.id(), existing);
            requireRouteCode(service, existing);
            if (!gatewayService.id().equals(existing.serviceId())) {
                throw new GatewayServiceOperationException(
                        "Super API Gateway Route 关联了其他 Service，拒绝接管"
                );
            }
            return client.updateRoute(existing.id(), updateRouteRequest(service, false));
        }
        try {
            return client.createRoute(new CreateRouteRequest(
                    gatewayService.id(),
                    service.code(),
                    service.name(),
                    service.routePath(),
                    METHODS,
                    ROUTE_ORDER,
                    STRIP_PREFIX_SEGMENTS,
                    false,
                    SuperApiGatewayAdminClient.SOURCE,
                    service.id().toString()
            ));
        } catch (SuperApiGatewayAdminException exception) {
            if (!exception.isConflict()) {
                throw exception;
            }
            RouteResponse concurrent = client.findRoute(service.id().toString())
                    .orElseThrow(() -> new GatewayServiceOperationException(
                            "Super API Gateway 中存在冲突的 Route，拒绝接管"
                    ));
            requireRouteOwnership(service.id(), concurrent);
            requireRouteCode(service, concurrent);
            if (!gatewayService.id().equals(concurrent.serviceId())) {
                throw new GatewayServiceOperationException(
                        "Super API Gateway Route 关联了其他 Service，拒绝接管"
                );
            }
            return client.updateRoute(concurrent.id(), updateRouteRequest(service, false));
        }
    }

    private GatewayInspectionResult inspectPresent(
            GatewayServiceSpec expected,
            GatewayServiceReference reference,
            ServiceResponse service,
            RouteResponse route
    ) {
        if (!owned(service.source(), service.externalId(), expected.id())
                || !owned(route.source(), route.externalId(), expected.id())
                || !service.id().equals(route.serviceId())) {
            return GatewayInspectionResult.drifted(
                    GatewayReconciliationReason.OWNER_MISMATCH,
                    "Super API Gateway Service、Route 的归属或关联关系不正确"
            );
        }
        if (reference != null
                && hasText(reference.externalServiceId())
                && !reference.externalServiceId().equals(service.id().toString())
                || reference != null
                && hasText(reference.externalRouteId())
                && !reference.externalRouteId().equals(route.id().toString())) {
            return GatewayInspectionResult.drifted(
                    GatewayReconciliationReason.CONFIG_MISMATCH,
                    "Super API Gateway 外部 ID 与本地 Binding 不一致"
            );
        }
        if (!expected.code().equals(service.code())
                || !expected.name().equals(service.name())
                || !expected.upstreamUrl().equals(service.upstreamUri())
                || !expected.accessMode().name().equals(service.accessMode())
                || service.connectTimeoutMs() != upstreamConnectTimeoutMs()
                || service.responseTimeoutMs() != upstreamResponseTimeoutMs()
                || !service.enabled()
                || !expected.code().equals(route.code())
                || !expected.name().equals(route.name())
                || !expected.routePath().equals(route.pathPattern())
                || !METHODS.equals(route.methods())
                || route.order() != ROUTE_ORDER
                || route.stripPrefixSegments() != STRIP_PREFIX_SEGMENTS
                || !route.enabled()) {
            return GatewayInspectionResult.drifted(
                    GatewayReconciliationReason.CONFIG_MISMATCH,
                    "Super API Gateway Service 或 Route 配置与本地发布期望不一致"
            );
        }
        return GatewayInspectionResult.inSync(
                "Super API Gateway Service、Route 和访问模式与本地期望一致"
        );
    }

    private UpdateServiceRequest updateServiceRequest(GatewayServiceSpec service, boolean enabled) {
        return new UpdateServiceRequest(
                service.name(),
                service.upstreamUrl(),
                service.accessMode().name(),
                upstreamConnectTimeoutMs(),
                upstreamResponseTimeoutMs(),
                enabled,
                description(service)
        );
    }

    private static UpdateRouteRequest updateRouteRequest(GatewayServiceSpec service, boolean enabled) {
        return new UpdateRouteRequest(
                service.name(),
                service.routePath(),
                METHODS,
                ROUTE_ORDER,
                STRIP_PREFIX_SEGMENTS,
                enabled
        );
    }

    private int upstreamConnectTimeoutMs() {
        return milliseconds(
                properties.superApiGateway().upstreamConnectTimeout(),
                100,
                120_000,
                "Super API Gateway 上游连接超时"
        );
    }

    private int upstreamResponseTimeoutMs() {
        return milliseconds(
                properties.superApiGateway().upstreamResponseTimeout(),
                100,
                600_000,
                "Super API Gateway 上游响应超时"
        );
    }

    private static String description(GatewayServiceSpec service) {
        return "Managed by DataScalpel service " + service.code();
    }

    private static void requireServiceOwnership(UUID id, ServiceResponse service) {
        if (!owned(service.source(), service.externalId(), id)) {
            throw new GatewayServiceOperationException(
                    "Super API Gateway Service 归属校验失败，拒绝接管或删除"
            );
        }
    }

    private static void requireRouteOwnership(UUID id, RouteResponse route) {
        if (!owned(route.source(), route.externalId(), id)) {
            throw new GatewayServiceOperationException(
                    "Super API Gateway Route 归属校验失败，拒绝接管或删除"
            );
        }
    }

    private static void requireServiceCode(GatewayServiceSpec expected, ServiceResponse actual) {
        if (!expected.code().equals(actual.code())) {
            throw new GatewayServiceOperationException(
                    "Super API Gateway Service code 与当前数据服务不一致，拒绝接管"
            );
        }
    }

    private static void requireRouteCode(GatewayServiceSpec expected, RouteResponse actual) {
        if (!expected.code().equals(actual.code())) {
            throw new GatewayServiceOperationException(
                    "Super API Gateway Route code 与当前数据服务不一致，拒绝接管"
            );
        }
    }

    private void deleteRoute(UUID id) {
        try {
            client.deleteRoute(id);
        } catch (SuperApiGatewayAdminException exception) {
            if (!exception.isNotFound()) {
                throw exception;
            }
        }
    }

    private static GatewayServiceOperationException operationFailure(String action, RuntimeException exception) {
        return new GatewayServiceOperationException(
                "Super API Gateway " + action + "失败：" + safeMessage(exception),
                exception
        );
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        String normalized = message == null || message.isBlank() ? "远程调用失败" : message.trim();
        return normalized.substring(0, Math.min(500, normalized.length()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

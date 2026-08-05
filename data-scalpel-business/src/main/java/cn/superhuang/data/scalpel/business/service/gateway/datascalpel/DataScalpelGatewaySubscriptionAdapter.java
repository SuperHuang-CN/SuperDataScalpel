package cn.superhuang.data.scalpel.business.service.gateway.datascalpel;

import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionOperationException;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionPort;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionReference;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionResult;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionSpec;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayAdapterSupport.owned;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.ConsumerResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.GrantSubscriptionRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.ServiceResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.SubscriptionResponse;

@Component
public class DataScalpelGatewaySubscriptionAdapter implements GatewaySubscriptionPort {

    private final SuperApiGatewayAdminClient client;

    public DataScalpelGatewaySubscriptionAdapter(SuperApiGatewayAdminClient client) {
        this.client = client;
    }

    @Override
    public GatewayProvider provider() {
        return GatewayProvider.DATASCALPEL;
    }

    @Override
    public GatewaySubscriptionResult grant(GatewaySubscriptionSpec subscription) {
        try {
            ConsumerResponse consumer = requireConsumer(
                    subscription.consumerId(),
                    subscription.consumerExternalId()
            );
            ServiceResponse service = requireService(
                    subscription.dataServiceId(),
                    subscription.serviceExternalId()
            );
            Optional<SubscriptionResponse> existing = client.findSubscription(
                    subscription.id().toString()
            );
            SubscriptionResponse result;
            if (existing.isPresent()) {
                verifyOwnership(subscription, consumer.id(), service.id(), existing.get());
                result = client.grantSubscription(new GrantSubscriptionRequest(
                        consumer.id(),
                        service.id(),
                        SuperApiGatewayAdminClient.SOURCE,
                        subscription.id().toString()
                ));
            } else {
                try {
                    result = client.grantSubscription(new GrantSubscriptionRequest(
                            consumer.id(),
                            service.id(),
                            SuperApiGatewayAdminClient.SOURCE,
                            subscription.id().toString()
                    ));
                } catch (SuperApiGatewayAdminException exception) {
                    if (!exception.isConflict()) {
                        throw exception;
                    }
                    result = client.findSubscription(subscription.id().toString())
                            .orElseThrow(() -> new GatewaySubscriptionOperationException(
                                    "Super API Gateway 中存在冲突的 Subscription，拒绝接管"
                            ));
                }
            }
            verifyOwnership(subscription, consumer.id(), service.id(), result);
            if (!"ACTIVE".equals(result.status())) {
                throw new GatewaySubscriptionOperationException(
                        "Super API Gateway 未返回有效的 Subscription 授权结果"
                );
            }
            return new GatewaySubscriptionResult(result.id().toString());
        } catch (GatewaySubscriptionOperationException exception) {
            throw exception;
        } catch (SuperApiGatewayAdminException | IllegalArgumentException exception) {
            throw operationFailure("Subscription 授权", exception);
        }
    }

    @Override
    public void revoke(GatewaySubscriptionReference subscription) {
        try {
            Optional<SubscriptionResponse> existing = client.findSubscription(
                    subscription.id().toString()
            );
            if (existing.isPresent()) {
                verifyOwnership(
                        subscription.id(),
                        subscription.consumerExternalId(),
                        subscription.serviceExternalId(),
                        existing.get()
                );
                if (hasText(subscription.externalMembershipId())
                        && !subscription.externalMembershipId().equals(existing.get().id().toString())) {
                    throw new GatewaySubscriptionOperationException(
                            "Super API Gateway Subscription 外部 ID 与本地 Binding 不一致"
                    );
                }
                SubscriptionResponse revoked = existing.get();
                if (!"REVOKED".equals(revoked.status())) {
                    revoked = client.revokeSubscription(revoked.id());
                }
                if (!"REVOKED".equals(revoked.status())) {
                    throw new GatewaySubscriptionOperationException(
                            "Super API Gateway 未确认 Subscription 已撤回"
                    );
                }
                deleteSubscription(revoked.id());
            }
            cleanupDisabledService(subscription.dataServiceId());
        } catch (GatewaySubscriptionOperationException exception) {
            throw exception;
        } catch (SuperApiGatewayAdminException | IllegalArgumentException exception) {
            throw operationFailure("Subscription 撤回", exception);
        }
    }

    @Override
    public GatewayInspectionResult inspect(GatewaySubscriptionInspectionSpec inspection) {
        try {
            GatewaySubscriptionSpec expected = inspection.expected();
            Optional<SubscriptionResponse> actual = client.findSubscription(expected.id().toString());
            if (!inspection.expectedPresent()) {
                if (actual.isEmpty()) {
                    return GatewayInspectionResult.inSync(
                            "Super API Gateway Subscription 已不存在，符合本地撤回期望"
                    );
                }
                if (!owned(actual.get().source(), actual.get().externalId(), expected.id())) {
                    return GatewayInspectionResult.drifted(
                            GatewayReconciliationReason.OWNER_MISMATCH,
                            "外部引用指向不属于当前订阅的 Super API Gateway Subscription"
                    );
                }
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.UNEXPECTED_REMOTE,
                        "本地期望撤回，但 Super API Gateway Subscription 仍然存在"
                );
            }
            if (actual.isEmpty()) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.REMOTE_MISSING,
                        "Super API Gateway Subscription 不存在"
                );
            }
            Optional<ConsumerResponse> consumer = client.findConsumer(expected.consumerId().toString());
            Optional<ServiceResponse> service = client.findService(expected.dataServiceId().toString());
            if (consumer.isEmpty() || service.isEmpty()) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.REMOTE_MISSING,
                        consumer.isEmpty() && service.isEmpty()
                                ? "Super API Gateway Consumer 和 Service 均不存在"
                                : consumer.isEmpty() ? "Super API Gateway Consumer 不存在"
                                : "Super API Gateway Service 不存在"
                );
            }
            SubscriptionResponse subscription = actual.get();
            if (!owned(subscription.source(), subscription.externalId(), expected.id())
                    || !owned(consumer.get().source(), consumer.get().externalId(), expected.consumerId())
                    || !owned(service.get().source(), service.get().externalId(), expected.dataServiceId())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.OWNER_MISMATCH,
                        "Super API Gateway Subscription 或关联资源归属不正确"
                );
            }
            if (!consumer.get().id().equals(subscription.consumerId())
                    || !service.get().id().equals(subscription.serviceId())
                    || !"ACTIVE".equals(subscription.status())
                    || hasText(expected.consumerExternalId())
                    && !expected.consumerExternalId().equals(consumer.get().id().toString())
                    || hasText(expected.serviceExternalId())
                    && !expected.serviceExternalId().equals(service.get().id().toString())
                    || hasText(inspection.externalMembershipId())
                    && !inspection.externalMembershipId().equals(subscription.id().toString())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.CONFIG_MISMATCH,
                        "Super API Gateway Subscription 状态、关联关系或外部 ID 不一致"
                );
            }
            return GatewayInspectionResult.inSync(
                    "Super API Gateway Subscription 与本地授权期望一致"
            );
        } catch (SuperApiGatewayAdminException | IllegalArgumentException exception) {
            throw operationFailure("Subscription 状态检查", exception);
        }
    }

    private ConsumerResponse requireConsumer(UUID consumerId, String externalId) {
        ConsumerResponse consumer = client.findConsumer(consumerId.toString())
                .orElseThrow(() -> new GatewaySubscriptionOperationException(
                        "Super API Gateway Consumer 不存在"
                ));
        if (!owned(consumer.source(), consumer.externalId(), consumerId)
                || hasText(externalId) && !externalId.equals(consumer.id().toString())) {
            throw new GatewaySubscriptionOperationException(
                    "Super API Gateway Consumer 归属或外部 ID 校验失败"
            );
        }
        if (!consumer.enabled()) {
            throw new GatewaySubscriptionOperationException(
                    "Super API Gateway Consumer 已停用"
            );
        }
        return consumer;
    }

    private ServiceResponse requireService(UUID serviceId, String externalId) {
        ServiceResponse service = client.findService(serviceId.toString())
                .orElseThrow(() -> new GatewaySubscriptionOperationException(
                        "Super API Gateway Service 不存在"
                ));
        if (!owned(service.source(), service.externalId(), serviceId)
                || hasText(externalId) && !externalId.equals(service.id().toString())) {
            throw new GatewaySubscriptionOperationException(
                    "Super API Gateway Service 归属或外部 ID 校验失败"
            );
        }
        if (!service.enabled() || !"SUBSCRIPTION_REQUIRED".equals(service.accessMode())) {
            throw new GatewaySubscriptionOperationException(
                    "Super API Gateway Service 未启用订阅访问模式"
            );
        }
        return service;
    }

    private static void verifyOwnership(
            GatewaySubscriptionSpec expected,
            UUID consumerId,
            UUID serviceId,
            SubscriptionResponse actual
    ) {
        requireOwnedSubscription(expected.id(), actual);
        if (!consumerId.equals(actual.consumerId()) || !serviceId.equals(actual.serviceId())) {
            throw new GatewaySubscriptionOperationException(
                    "Super API Gateway Subscription 关联了其他 Consumer 或 Service"
            );
        }
    }

    private static void verifyOwnership(
            UUID subscriptionId,
            String consumerExternalId,
            String serviceExternalId,
            SubscriptionResponse actual
    ) {
        requireOwnedSubscription(subscriptionId, actual);
        if (hasText(consumerExternalId)
                && !consumerExternalId.equals(actual.consumerId().toString())
                || hasText(serviceExternalId)
                && !serviceExternalId.equals(actual.serviceId().toString())) {
            throw new GatewaySubscriptionOperationException(
                    "Super API Gateway Subscription 关联了其他 Consumer 或 Service"
            );
        }
    }

    private static void requireOwnedSubscription(
            UUID subscriptionId,
            SubscriptionResponse actual
    ) {
        if (!owned(actual.source(), actual.externalId(), subscriptionId)) {
            throw new GatewaySubscriptionOperationException(
                    "Super API Gateway Subscription 归属校验失败，拒绝接管或删除"
            );
        }
        if (actual.consumerId() == null || actual.serviceId() == null) {
            throw new GatewaySubscriptionOperationException(
                    "Super API Gateway Subscription 缺少关联资源"
            );
        }
    }

    private void deleteSubscription(UUID id) {
        try {
            client.deleteSubscription(id);
        } catch (SuperApiGatewayAdminException exception) {
            if (!exception.isNotFound()) {
                throw exception;
            }
        }
    }

    private void cleanupDisabledService(UUID dataServiceId) {
        Optional<ServiceResponse> service = client.findService(dataServiceId.toString());
        if (service.isEmpty() || service.get().enabled()) {
            return;
        }
        if (!owned(service.get().source(), service.get().externalId(), dataServiceId)) {
            throw new GatewaySubscriptionOperationException(
                    "Super API Gateway Service 归属校验失败，拒绝清理"
            );
        }
        try {
            client.deleteService(service.get().id());
        } catch (SuperApiGatewayAdminException exception) {
            if (!exception.isNotFound() && !exception.isConflict()) {
                throw exception;
            }
            // Another active subscription or concurrent republish keeps the Service.
        }
    }

    private static GatewaySubscriptionOperationException operationFailure(
            String action,
            RuntimeException exception
    ) {
        return new GatewaySubscriptionOperationException(
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

package cn.superhuang.data.scalpel.business.service.gateway.datascalpel;

import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerOperationException;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerPort;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerReference;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerResult;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerSpec;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayAdapterSupport.equalText;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayAdapterSupport.owned;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.ConsumerResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.CreateConsumerRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.UpdateConsumerRequest;

@Component
public class DataScalpelGatewayConsumerAdapter implements GatewayConsumerPort {

    private final SuperApiGatewayAdminClient client;

    public DataScalpelGatewayConsumerAdapter(SuperApiGatewayAdminClient client) {
        this.client = client;
    }

    @Override
    public GatewayProvider provider() {
        return GatewayProvider.DATASCALPEL;
    }

    @Override
    public GatewayConsumerResult upsert(GatewayConsumerSpec consumer) {
        try {
            ConsumerResponse result = client.findConsumer(consumer.id().toString())
                    .map(existing -> updateOwned(consumer, existing))
                    .orElseGet(() -> createOrConverge(consumer));
            verifySynchronized(consumer, result);
            return new GatewayConsumerResult(result.id().toString());
        } catch (GatewayConsumerOperationException exception) {
            throw exception;
        } catch (SuperApiGatewayAdminException | IllegalArgumentException exception) {
            throw operationFailure("Consumer 同步", exception);
        }
    }

    @Override
    public void remove(GatewayConsumerReference consumer) {
        try {
            Optional<ConsumerResponse> existing = client.findConsumer(consumer.id().toString());
            if (existing.isEmpty()) {
                return;
            }
            requireOwnership(consumer.id(), existing.get());
            if (existing.get().enabled()) {
                client.setConsumerEnabled(existing.get().id(), false);
            }
            try {
                client.deleteConsumer(existing.get().id());
            } catch (SuperApiGatewayAdminException exception) {
                if (!exception.isNotFound()) {
                    throw exception;
                }
            }
        } catch (GatewayConsumerOperationException exception) {
            throw exception;
        } catch (SuperApiGatewayAdminException | IllegalArgumentException exception) {
            throw operationFailure("Consumer 删除", exception);
        }
    }

    @Override
    public GatewayInspectionResult inspect(GatewayConsumerInspectionSpec inspection) {
        try {
            GatewayConsumerSpec expected = inspection.expected();
            Optional<ConsumerResponse> actual = client.findConsumer(expected.id().toString());
            if (!inspection.expectedPresent()) {
                if (actual.isEmpty()) {
                    return GatewayInspectionResult.inSync(
                            "Super API Gateway Consumer 已不存在，符合本地删除期望"
                    );
                }
                if (!owned(actual.get().source(), actual.get().externalId(), expected.id())) {
                    return GatewayInspectionResult.drifted(
                            GatewayReconciliationReason.OWNER_MISMATCH,
                            "外部引用指向不属于当前消费者的 Super API Gateway Consumer"
                    );
                }
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.UNEXPECTED_REMOTE,
                        "本地期望删除，但 Super API Gateway Consumer 仍然存在"
                );
            }
            if (actual.isEmpty()) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.REMOTE_MISSING,
                        "Super API Gateway Consumer 不存在"
                );
            }
            ConsumerResponse consumer = actual.get();
            if (!owned(consumer.source(), consumer.externalId(), expected.id())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.OWNER_MISMATCH,
                        "Super API Gateway Consumer 归属与当前消费者不一致"
                );
            }
            if (!expected.code().equals(consumer.code())
                    || !expected.name().equals(consumer.name())
                    || !equalText(expected.description(), consumer.description())
                    || !consumer.enabled()
                    || hasText(inspection.reference().externalId())
                    && !inspection.reference().externalId().equals(consumer.id().toString())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.CONFIG_MISMATCH,
                        "Super API Gateway Consumer 配置或外部 ID 与本地期望不一致"
                );
            }
            return GatewayInspectionResult.inSync(
                    "Super API Gateway Consumer 与本地期望一致"
            );
        } catch (SuperApiGatewayAdminException | IllegalArgumentException exception) {
            throw operationFailure("Consumer 状态检查", exception);
        }
    }

    private ConsumerResponse createOrConverge(GatewayConsumerSpec consumer) {
        try {
            return client.createConsumer(new CreateConsumerRequest(
                    consumer.code(),
                    consumer.name(),
                    true,
                    consumer.description(),
                    SuperApiGatewayAdminClient.SOURCE,
                    consumer.id().toString()
            ));
        } catch (SuperApiGatewayAdminException exception) {
            if (!exception.isConflict()) {
                throw exception;
            }
            ConsumerResponse concurrent = client.findConsumer(consumer.id().toString())
                    .orElseThrow(() -> new GatewayConsumerOperationException(
                            "Super API Gateway 中存在冲突的 Consumer，拒绝接管"
                    ));
            return updateOwned(consumer, concurrent);
        }
    }

    private ConsumerResponse updateOwned(GatewayConsumerSpec expected, ConsumerResponse actual) {
        requireOwnership(expected.id(), actual);
        if (!expected.code().equals(actual.code())) {
            throw new GatewayConsumerOperationException(
                    "Super API Gateway Consumer code 与当前消费者不一致，拒绝接管"
            );
        }
        return client.updateConsumer(
                actual.id(),
                new UpdateConsumerRequest(expected.name(), true, expected.description())
        );
    }

    private static void verifySynchronized(GatewayConsumerSpec expected, ConsumerResponse actual) {
        requireOwnership(expected.id(), actual);
        if (!expected.code().equals(actual.code()) || !actual.enabled()) {
            throw new GatewayConsumerOperationException(
                    "Super API Gateway 未返回匹配的 Consumer 同步结果"
            );
        }
    }

    private static void requireOwnership(java.util.UUID id, ConsumerResponse consumer) {
        if (!owned(consumer.source(), consumer.externalId(), id)) {
            throw new GatewayConsumerOperationException(
                    "Super API Gateway Consumer 归属校验失败，拒绝接管或删除"
            );
        }
    }

    private static GatewayConsumerOperationException operationFailure(
            String action,
            RuntimeException exception
    ) {
        return new GatewayConsumerOperationException(
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

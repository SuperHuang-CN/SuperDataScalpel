package cn.superhuang.data.scalpel.business.service.gateway.datascalpel;

import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialOperationException;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialPort;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialReference;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialResult;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialSpec;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayAdapterSupport.owned;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.ApiKeyDetailResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.ApiKeyResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.ConsumerResponse;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.CreateApiKeyRequest;
import static cn.superhuang.data.scalpel.business.service.gateway.datascalpel.SuperApiGatewayModels.RotateApiKeyRequest;

@Component
public class DataScalpelGatewayCredentialAdapter implements GatewayCredentialPort {

    private final SuperApiGatewayAdminClient client;

    public DataScalpelGatewayCredentialAdapter(SuperApiGatewayAdminClient client) {
        this.client = client;
    }

    @Override
    public GatewayProvider provider() {
        return GatewayProvider.DATASCALPEL;
    }

    @Override
    public GatewayCredentialResult upsert(GatewayCredentialSpec credential) {
        try {
            String secret = required(credential.secret(), "API Key");
            ConsumerResponse consumer = requireConsumer(
                    credential.consumerId(),
                    credential.consumerExternalId()
            );
            Optional<ApiKeyResponse> existing = client.findApiKey(
                    consumer.id(),
                    credential.id().toString()
            );
            ApiKeyResponse result;
            if (existing.isPresent()) {
                requireOwnership(credential.id(), consumer.id(), existing.get());
                result = client.rotateApiKey(
                        consumer.id(),
                        existing.get().id(),
                        new RotateApiKeyRequest(secret)
                );
            } else {
                try {
                    result = client.createApiKey(
                            consumer.id(),
                            new CreateApiKeyRequest(
                                    keyName(credential.id()),
                                    SuperApiGatewayAdminClient.SOURCE,
                                    credential.id().toString(),
                                    secret
                            )
                    );
                } catch (SuperApiGatewayAdminException exception) {
                    if (!exception.isConflict()) {
                        throw exception;
                    }
                    ApiKeyResponse concurrent = client.findApiKey(
                                    consumer.id(),
                                    credential.id().toString()
                            )
                            .orElseThrow(() -> new GatewayCredentialOperationException(
                                    "Super API Gateway 中存在冲突的 API Key，拒绝接管"
                            ));
                    requireOwnership(credential.id(), consumer.id(), concurrent);
                    result = client.rotateApiKey(
                            consumer.id(),
                            concurrent.id(),
                            new RotateApiKeyRequest(secret)
                    );
                }
            }
            requireOwnership(credential.id(), consumer.id(), result);
            if (!"ACTIVE".equals(result.status())) {
                throw new GatewayCredentialOperationException(
                        "Super API Gateway 未返回有效的 API Key 同步结果"
                );
            }
            return new GatewayCredentialResult(result.id().toString());
        } catch (GatewayCredentialOperationException exception) {
            throw exception;
        } catch (SuperApiGatewayAdminException | IllegalArgumentException exception) {
            throw operationFailure("API Key 同步", exception);
        }
    }

    @Override
    public void remove(GatewayCredentialReference credential) {
        try {
            Optional<ConsumerResponse> consumer = client.findConsumer(credential.consumerId().toString());
            if (consumer.isEmpty()) {
                return;
            }
            verifyConsumer(credential.consumerId(), credential.consumerExternalId(), consumer.get());
            Optional<ApiKeyResponse> key = client.findApiKey(
                    consumer.get().id(),
                    credential.id().toString()
            );
            if (key.isEmpty()) {
                return;
            }
            requireOwnership(credential.id(), consumer.get().id(), key.get());
            if (hasText(credential.externalId())
                    && !credential.externalId().equals(key.get().id().toString())) {
                throw new GatewayCredentialOperationException(
                        "Super API Gateway API Key 外部 ID 与本地 Binding 不一致，拒绝删除"
                );
            }
            try {
                client.deleteApiKey(consumer.get().id(), key.get().id());
            } catch (SuperApiGatewayAdminException exception) {
                if (!exception.isNotFound()) {
                    throw exception;
                }
            }
        } catch (GatewayCredentialOperationException exception) {
            throw exception;
        } catch (SuperApiGatewayAdminException | IllegalArgumentException exception) {
            throw operationFailure("API Key 删除", exception);
        }
    }

    @Override
    public GatewayInspectionResult inspect(GatewayCredentialInspectionSpec inspection) {
        try {
            Optional<ConsumerResponse> consumer = client.findConsumer(inspection.consumerId().toString());
            if (consumer.isEmpty()) {
                return inspection.expectedPresent()
                        ? GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.REMOTE_MISSING,
                        "Super API Gateway Consumer 不存在，API Key 无法存在"
                )
                        : GatewayInspectionResult.inSync(
                        "Super API Gateway Consumer 和 API Key 均不存在，符合删除期望"
                );
            }
            if (!owned(
                    consumer.get().source(),
                    consumer.get().externalId(),
                    inspection.consumerId()
            )) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.OWNER_MISMATCH,
                        "Super API Gateway Consumer 不属于当前消费者"
                );
            }
            if (hasText(inspection.consumerExternalId())
                    && !inspection.consumerExternalId().equals(consumer.get().id().toString())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.CONFIG_MISMATCH,
                        "Super API Gateway Consumer 外部 ID 与本地 Binding 不一致"
                );
            }

            Optional<ApiKeyResponse> key = client.findApiKey(
                    consumer.get().id(),
                    inspection.id().toString()
            );
            if (!inspection.expectedPresent()) {
                if (key.isEmpty()) {
                    return GatewayInspectionResult.inSync(
                            "Super API Gateway API Key 已不存在，符合本地删除期望"
                    );
                }
                if (!isOwned(inspection.id(), consumer.get().id(), key.get())) {
                    return GatewayInspectionResult.drifted(
                            GatewayReconciliationReason.OWNER_MISMATCH,
                            "外部引用指向不属于当前凭证的 Super API Gateway API Key"
                    );
                }
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.UNEXPECTED_REMOTE,
                        "本地期望删除，但 Super API Gateway API Key 仍然存在"
                );
            }
            if (key.isEmpty()) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.REMOTE_MISSING,
                        "Super API Gateway API Key 不存在；本地不保存明文，必须轮换后恢复"
                );
            }
            if (!isOwned(inspection.id(), consumer.get().id(), key.get())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.OWNER_MISMATCH,
                        "Super API Gateway API Key 归属或所属 Consumer 不一致"
                );
            }
            if (hasText(inspection.externalId())
                    && !inspection.externalId().equals(key.get().id().toString())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.CONFIG_MISMATCH,
                        "Super API Gateway API Key 外部 ID 与本地 Binding 不一致"
                );
            }
            ApiKeyDetailResponse detail = client.getApiKey(consumer.get().id(), key.get().id());
            if (!"ACTIVE".equals(detail.status())
                    || !keyName(inspection.id()).equals(detail.name())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.CONFIG_MISMATCH,
                        "Super API Gateway API Key 状态或名称与本地期望不一致"
                );
            }
            if (!inspection.secretDigest().equalsIgnoreCase(detail.secretDigest())) {
                return GatewayInspectionResult.drifted(
                        GatewayReconciliationReason.SECRET_MISMATCH,
                        "Super API Gateway API Key 与本地摘要不一致，必须轮换凭证"
                );
            }
            return GatewayInspectionResult.inSync(
                    "Super API Gateway API Key 与本地期望一致"
            );
        } catch (SuperApiGatewayAdminException | IllegalArgumentException exception) {
            throw operationFailure("API Key 状态检查", exception);
        }
    }

    private ConsumerResponse requireConsumer(UUID consumerId, String externalId) {
        ConsumerResponse consumer = client.findConsumer(consumerId.toString())
                .orElseThrow(() -> new GatewayCredentialOperationException(
                        "Super API Gateway Consumer 不存在"
                ));
        verifyConsumer(consumerId, externalId, consumer);
        if (!consumer.enabled()) {
            throw new GatewayCredentialOperationException(
                    "Super API Gateway Consumer 已停用"
            );
        }
        return consumer;
    }

    private static void verifyConsumer(
            UUID consumerId,
            String expectedExternalId,
            ConsumerResponse consumer
    ) {
        if (!owned(consumer.source(), consumer.externalId(), consumerId)) {
            throw new GatewayCredentialOperationException(
                    "Super API Gateway Consumer 归属校验失败"
            );
        }
        if (hasText(expectedExternalId)
                && !expectedExternalId.equals(consumer.id().toString())) {
            throw new GatewayCredentialOperationException(
                    "Super API Gateway Consumer 外部 ID 与本地 Binding 不一致"
            );
        }
    }

    private static void requireOwnership(
            UUID credentialId,
            UUID consumerId,
            ApiKeyResponse key
    ) {
        if (!isOwned(credentialId, consumerId, key)) {
            throw new GatewayCredentialOperationException(
                    "Super API Gateway API Key 归属校验失败，拒绝接管或删除"
            );
        }
    }

    private static boolean isOwned(
            UUID credentialId,
            UUID consumerId,
            ApiKeyResponse key
    ) {
        return key != null
                && consumerId.equals(key.consumerId())
                && owned(key.source(), key.externalId(), credentialId);
    }

    private static String keyName(UUID credentialId) {
        return "ds-key-" + credentialId;
    }

    private static String required(String value, String label) {
        if (!hasText(value)) {
            throw new GatewayCredentialOperationException(label + " 未配置");
        }
        return value.trim();
    }

    private static GatewayCredentialOperationException operationFailure(
            String action,
            RuntimeException exception
    ) {
        return new GatewayCredentialOperationException(
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

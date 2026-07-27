package cn.superhuang.data.scalpel.business.service.consumer.credential.service;

import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.ApiConsumerCredential;
import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.GatewayCredentialBinding;
import cn.superhuang.data.scalpel.business.service.consumer.credential.domain.GatewayCredentialStatus;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialPortRegistry;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialReference;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialResult;
import cn.superhuang.data.scalpel.business.service.consumer.credential.gateway.GatewayCredentialSpec;
import cn.superhuang.data.scalpel.business.service.consumer.credential.repository.ApiConsumerCredentialRepository;
import cn.superhuang.data.scalpel.business.service.consumer.credential.repository.GatewayCredentialBindingRepository;
import cn.superhuang.data.scalpel.business.service.consumer.credential.web.request.CreateApiConsumerCredentialRequest;
import cn.superhuang.data.scalpel.business.service.consumer.credential.web.response.ApiConsumerCredentialResponse;
import cn.superhuang.data.scalpel.business.service.consumer.credential.web.response.ApiConsumerCredentialSecretResponse;
import cn.superhuang.data.scalpel.business.service.consumer.domain.ApiConsumer;
import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerBinding;
import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerSyncStatus;
import cn.superhuang.data.scalpel.business.service.consumer.repository.ApiConsumerRepository;
import cn.superhuang.data.scalpel.business.service.consumer.repository.GatewayConsumerBindingRepository;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ApiConsumerCredentialService {

    private static final int MAX_CREDENTIALS_PER_CONSUMER = 10;
    private static final Duration RECENT_OPERATION_TIMEOUT = Duration.ofSeconds(30);

    private final ApiConsumerRepository consumerRepository;
    private final GatewayConsumerBindingRepository consumerBindingRepository;
    private final ApiConsumerCredentialRepository credentialRepository;
    private final GatewayCredentialBindingRepository bindingRepository;
    private final GatewayCredentialPortRegistry portRegistry;
    private final TransactionTemplate transactionTemplate;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiConsumerCredentialService(
            ApiConsumerRepository consumerRepository,
            GatewayConsumerBindingRepository consumerBindingRepository,
            ApiConsumerCredentialRepository credentialRepository,
            GatewayCredentialBindingRepository bindingRepository,
            GatewayCredentialPortRegistry portRegistry,
            PlatformTransactionManager transactionManager
    ) {
        this.consumerRepository = consumerRepository;
        this.consumerBindingRepository = consumerBindingRepository;
        this.credentialRepository = credentialRepository;
        this.bindingRepository = bindingRepository;
        this.portRegistry = portRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public List<ApiConsumerCredentialResponse> list(UUID consumerId) {
        requireConsumer(consumerId);
        List<ApiConsumerCredential> credentials =
                credentialRepository.findAllByConsumerIdOrderByCreatedAtDesc(consumerId);
        Map<UUID, List<GatewayCredentialBinding>> bindings = bindingsByCredentialId(credentials);
        return credentials.stream()
                .map(credential -> ApiConsumerCredentialResponse.from(
                        credential,
                        bindings.getOrDefault(credential.getId(), List.of())
                ))
                .toList();
    }

    public ApiConsumerCredentialSecretResponse create(
            UUID consumerId,
            CreateApiConsumerCredentialRequest request
    ) {
        GatewayProvider provider = portRegistry.activeProvider();
        GeneratedSecret generated = generateSecret();
        SyncPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            ApiConsumer consumer = requireConsumerForUpdate(consumerId);
            GatewayConsumerBinding consumerBinding = requireCurrentConsumerBinding(consumer, provider);
            if (credentialRepository.countByConsumerId(consumerId) >= MAX_CREDENTIALS_PER_CONSUMER) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "每个消费者最多创建 10 个 API Key");
            }
            ApiConsumerCredential credential = credentialRepository.saveAndFlush(ApiConsumerCredential.create(
                    consumerId, request.name(), generated.digest(), generated.hint()
            ));
            GatewayCredentialBinding binding = GatewayCredentialBinding.pending(credential.getId(), provider);
            binding.beginSync();
            bindingRepository.saveAndFlush(binding);
            return preparation(consumer, consumerBinding, credential, binding, generated.secret());
        }));
        return executeSync(preparation);
    }

    public ApiConsumerCredentialSecretResponse rotate(UUID consumerId, UUID credentialId) {
        GatewayProvider provider = portRegistry.activeProvider();
        GeneratedSecret generated = generateSecret();
        SyncPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            ApiConsumer consumer = requireConsumerForUpdate(consumerId);
            GatewayConsumerBinding consumerBinding = requireCurrentConsumerBinding(consumer, provider);
            ApiConsumerCredential credential = requireCredentialForUpdate(consumerId, credentialId);
            assertNoRecentOperation(credentialId);
            credential.rotate(generated.digest(), generated.hint());
            credentialRepository.saveAndFlush(credential);
            GatewayCredentialBinding binding = bindingRepository
                    .findByCredentialIdAndProviderForUpdate(credentialId, provider)
                    .orElseGet(() -> GatewayCredentialBinding.pending(credentialId, provider));
            binding.beginSync();
            bindingRepository.saveAndFlush(binding);
            return preparation(consumer, consumerBinding, credential, binding, generated.secret());
        }));
        return executeSync(preparation);
    }

    public ApiConsumerCredentialResponse reconcileGateway(UUID consumerId, UUID credentialId) {
        List<ReconciliationPreparation> preparations = requireTransactionResult(
                transactionTemplate.execute(
                        status -> prepareReconciliation(consumerId, credentialId)
                )
        );
        List<ReconciliationAttempt> attempts = new ArrayList<>(preparations.size());
        for (ReconciliationPreparation preparation : preparations) {
            GatewayInspectionResult result = preparation.localResult();
            String failure = null;
            if (result == null) {
                try {
                    result = portRegistry.require(preparation.provider()).inspect(preparation.inspection());
                    if (result == null) throw new IllegalStateException("网关未返回 API Key 对账结果");
                } catch (RuntimeException exception) {
                    failure = safeMessage(exception);
                }
            }
            attempts.add(new ReconciliationAttempt(preparation, result, failure));
        }
        transactionTemplate.executeWithoutResult(status -> completeReconciliation(attempts));
        return requireTransactionResult(transactionTemplate.execute(status -> {
            ApiConsumerCredential credential = requireCredentialForUpdate(consumerId, credentialId);
            return response(credential);
        }));
    }

    public void delete(UUID consumerId, UUID credentialId) {
        DeletePlan plan = requireTransactionResult(transactionTemplate.execute(
                status -> prepareDelete(consumerId, credentialId)
        ));
        if (plan.credentialDeleted()) {
            return;
        }
        List<String> failures = new ArrayList<>();
        for (DeletePreparation preparation : plan.bindings()) {
            try {
                portRegistry.require(preparation.provider()).remove(new GatewayCredentialReference(
                        plan.credentialId(),
                        plan.consumerId(),
                        plan.consumerCode(),
                        preparation.consumerExternalId(),
                        preparation.externalId()
                ));
                transactionTemplate.executeWithoutResult(status -> completeBindingDelete(preparation));
            } catch (RuntimeException exception) {
                String failure = safeMessage(exception);
                failures.add(preparation.provider() + "：" + failure);
                transactionTemplate.executeWithoutResult(
                        status -> failBindingDelete(preparation, failure)
                );
            }
        }
        boolean deleted = requireTransactionResult(transactionTemplate.execute(
                status -> completeCredentialDelete(plan.consumerId(), plan.credentialId())
        ));
        if (!failures.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "网关 API Key 删除失败：" + String.join("；", failures)
            );
        }
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "API Key 绑定状态已发生变化，请重新删除");
        }
    }

    private ApiConsumerCredentialSecretResponse executeSync(SyncPreparation preparation) {
        String externalId = null;
        String failure = null;
        try {
            GatewayCredentialResult result = portRegistry.require(preparation.provider()).upsert(preparation.spec());
            if (result == null || result.externalId() == null || result.externalId().isBlank()) {
                throw new IllegalStateException("网关未返回 API Key 外部 ID");
            }
            externalId = result.externalId();
        } catch (RuntimeException exception) {
            failure = safeMessage(exception);
        }
        String finalExternalId = externalId;
        String finalFailure = failure;
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeSync(preparation, finalExternalId, finalFailure)
        ));
    }

    private ApiConsumerCredentialSecretResponse completeSync(
            SyncPreparation preparation,
            String externalId,
            String failure
    ) {
        ApiConsumerCredential credential = requireCredentialForUpdate(
                preparation.consumerId(), preparation.credentialId()
        );
        GatewayCredentialBinding binding = bindingRepository.findByIdForUpdate(preparation.bindingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "API Key 网关绑定不存在"));
        boolean current = credential.getRevision() == preparation.spec().revision()
                && binding.getStatus() == GatewayCredentialStatus.SYNC_PENDING
                && preparation.operationId().equals(binding.getOperationId());
        if (current) {
            if (failure == null) {
                binding.activated(externalId, credential.getRevision());
            } else {
                binding.syncFailed(failure);
            }
            bindingRepository.saveAndFlush(binding);
        }
        return new ApiConsumerCredentialSecretResponse(
                response(credential),
                current && failure == null ? preparation.secret() : null
        );
    }

    private DeletePlan prepareDelete(UUID consumerId, UUID credentialId) {
        ApiConsumer consumer = requireConsumerForUpdate(consumerId);
        ApiConsumerCredential credential = requireCredentialForUpdate(consumerId, credentialId);
        List<GatewayCredentialBinding> bindings = bindingRepository.findAllByCredentialId(credentialId);
        if (bindings.isEmpty()) {
            credentialRepository.delete(credential);
            credentialRepository.flush();
            return new DeletePlan(consumerId, consumer.getCode(), credentialId, true, List.of());
        }
        assertNoRecentOperation(bindings);
        List<DeletePreparation> preparations = new ArrayList<>(bindings.size());
        for (GatewayCredentialBinding binding : bindings) {
            GatewayConsumerBinding consumerBinding = consumerBindingRepository
                    .findByConsumerIdAndProvider(consumerId, binding.getProvider())
                    .orElse(null);
            binding.beginDelete();
            bindingRepository.saveAndFlush(binding);
            preparations.add(new DeletePreparation(
                    binding.getId(),
                    binding.getProvider(),
                    consumerBinding == null ? consumer.getCode() : consumerBinding.getExternalId(),
                    binding.getExternalId(),
                    binding.getOperationId()
            ));
        }
        return new DeletePlan(consumerId, consumer.getCode(), credentialId, false, List.copyOf(preparations));
    }

    private List<ReconciliationPreparation> prepareReconciliation(
            UUID consumerId,
            UUID credentialId
    ) {
        ApiConsumer consumer = requireConsumerForUpdate(consumerId);
        ApiConsumerCredential credential = requireCredentialForUpdate(consumerId, credentialId);
        List<GatewayCredentialBinding> bindings =
                bindingRepository.findAllByCredentialIdForUpdate(credentialId);
        if (bindings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "API Key 尚无可对账的网关绑定");
        }
        assertNoRecentOperation(bindings);
        List<ReconciliationPreparation> preparations = new ArrayList<>(bindings.size());
        for (GatewayCredentialBinding binding : bindings) {
            GatewayConsumerBinding consumerBinding = consumerBindingRepository
                    .findByConsumerIdAndProvider(consumerId, binding.getProvider())
                    .orElse(null);
            UUID operationId = binding.beginReconciliation();
            bindingRepository.saveAndFlush(binding);
            GatewayInspectionResult localResult = consumerBinding == null
                    || consumerBinding.getExternalId() == null
                    || consumerBinding.getExternalId().isBlank()
                    ? GatewayInspectionResult.drifted(
                            GatewayReconciliationReason.LOCAL_BINDING_MISSING,
                            "缺少同一网关的 Consumer 绑定，无法可靠检查 API Key"
                    )
                    : null;
            preparations.add(new ReconciliationPreparation(
                    binding.getId(),
                    binding.getProvider(),
                    operationId,
                    localResult == null ? new GatewayCredentialInspectionSpec(
                            credential.getId(),
                            consumer.getId(),
                            consumer.getCode(),
                            consumerBinding.getExternalId(),
                            binding.getExternalId(),
                            credential.getSecretDigest(),
                            binding.getStatus() != GatewayCredentialStatus.DELETE_PENDING
                                    && binding.getStatus() != GatewayCredentialStatus.DELETE_FAILED
                    ) : null,
                    localResult
            ));
        }
        return List.copyOf(preparations);
    }

    private void completeReconciliation(List<ReconciliationAttempt> attempts) {
        for (ReconciliationAttempt attempt : attempts) {
            GatewayCredentialBinding binding = bindingRepository
                    .findByIdForUpdate(attempt.preparation().bindingId())
                    .orElse(null);
            if (binding == null) continue;
            if (attempt.failure() == null) {
                binding.reconciled(attempt.preparation().operationId(), attempt.result());
            } else {
                binding.reconciliationFailed(attempt.preparation().operationId(), attempt.failure());
            }
            bindingRepository.save(binding);
        }
        bindingRepository.flush();
    }

    private void completeBindingDelete(DeletePreparation preparation) {
        GatewayCredentialBinding binding = bindingRepository.findByIdForUpdate(preparation.bindingId()).orElse(null);
        if (binding != null
                && binding.getStatus() == GatewayCredentialStatus.DELETE_PENDING
                && preparation.operationId().equals(binding.getOperationId())) {
            bindingRepository.delete(binding);
            bindingRepository.flush();
        }
    }

    private void failBindingDelete(DeletePreparation preparation, String failure) {
        GatewayCredentialBinding binding = bindingRepository.findByIdForUpdate(preparation.bindingId()).orElse(null);
        if (binding != null
                && binding.getStatus() == GatewayCredentialStatus.DELETE_PENDING
                && preparation.operationId().equals(binding.getOperationId())) {
            binding.deleteFailed(failure);
            bindingRepository.saveAndFlush(binding);
        }
    }

    private boolean completeCredentialDelete(UUID consumerId, UUID credentialId) {
        ApiConsumerCredential credential = credentialRepository.findByIdForUpdate(credentialId).orElse(null);
        if (credential == null) return true;
        if (!consumerId.equals(credential.getConsumerId()) || bindingRepository.existsByCredentialId(credentialId)) {
            return false;
        }
        credentialRepository.delete(credential);
        credentialRepository.flush();
        return true;
    }

    private SyncPreparation preparation(
            ApiConsumer consumer,
            GatewayConsumerBinding consumerBinding,
            ApiConsumerCredential credential,
            GatewayCredentialBinding binding,
            String secret
    ) {
        return new SyncPreparation(
                consumer.getId(),
                credential.getId(),
                binding.getId(),
                binding.getProvider(),
                binding.getOperationId(),
                secret,
                new GatewayCredentialSpec(
                        credential.getId(),
                        consumer.getId(),
                        consumer.getCode(),
                        consumerBinding.getExternalId(),
                        secret,
                        credential.getRevision()
                )
        );
    }

    private GatewayConsumerBinding requireCurrentConsumerBinding(
            ApiConsumer consumer,
            GatewayProvider provider
    ) {
        GatewayConsumerBinding binding = consumerBindingRepository
                .findByConsumerIdAndProvider(consumer.getId(), provider)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "消费者尚未同步到当前网关"
                ));
        if (binding.getSyncStatus() != GatewayConsumerSyncStatus.SYNCED
                || binding.getSyncedRevision() != consumer.getRevision()
                || binding.getExternalId() == null || binding.getExternalId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "消费者当前版本尚未同步到网关");
        }
        return binding;
    }

    private void assertNoRecentOperation(UUID credentialId) {
        assertNoRecentOperation(bindingRepository.findAllByCredentialId(credentialId));
    }

    private void assertNoRecentOperation(List<GatewayCredentialBinding> bindings) {
        Instant now = Instant.now();
        if (bindings.stream().anyMatch(binding -> binding.hasRecentOperation(now, RECENT_OPERATION_TIMEOUT))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "API Key 正在执行网关操作，请稍后重试");
        }
    }

    private ApiConsumerCredentialResponse response(ApiConsumerCredential credential) {
        return ApiConsumerCredentialResponse.from(
                credential,
                bindingRepository.findAllByCredentialId(credential.getId())
        );
    }

    private Map<UUID, List<GatewayCredentialBinding>> bindingsByCredentialId(
            Collection<ApiConsumerCredential> credentials
    ) {
        if (credentials.isEmpty()) return Map.of();
        Map<UUID, List<GatewayCredentialBinding>> result = new HashMap<>();
        bindingRepository.findAllByCredentialIdIn(credentials.stream().map(ApiConsumerCredential::getId).toList())
                .forEach(binding -> result.computeIfAbsent(
                        binding.getCredentialId(), ignored -> new ArrayList<>()
                ).add(binding));
        return result;
    }

    private ApiConsumer requireConsumer(UUID id) {
        return consumerRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消费者不存在"));
    }

    private ApiConsumer requireConsumerForUpdate(UUID id) {
        return consumerRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消费者不存在"));
    }

    private ApiConsumerCredential requireCredentialForUpdate(UUID consumerId, UUID credentialId) {
        ApiConsumerCredential credential = credentialRepository.findByIdForUpdate(credentialId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API Key 不存在"));
        if (!consumerId.equals(credential.getConsumerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "API Key 不存在");
        }
        return credential;
    }

    private GeneratedSecret generateSecret() {
        byte[] random = new byte[32];
        secureRandom.nextBytes(random);
        String secret = "dsk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        String digest;
        try {
            digest = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境不支持 SHA-256", exception);
        }
        return new GeneratedSecret(secret, digest, "dsk_…" + secret.substring(secret.length() - 6));
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception instanceof ResponseStatusException response
                ? response.getReason()
                : exception.getMessage();
        String normalized = message == null || message.isBlank() ? "远程调用失败" : message.trim();
        return normalized.substring(0, Math.min(1000, normalized.length()));
    }

    private static <T> T requireTransactionResult(T value) {
        if (value == null) throw new IllegalStateException("事务未返回 API Key 处理结果");
        return value;
    }

    private record GeneratedSecret(String secret, String digest, String hint) {
    }

    private record SyncPreparation(
            UUID consumerId,
            UUID credentialId,
            UUID bindingId,
            GatewayProvider provider,
            UUID operationId,
            String secret,
            GatewayCredentialSpec spec
    ) {
    }

    private record DeletePlan(
            UUID consumerId,
            String consumerCode,
            UUID credentialId,
            boolean credentialDeleted,
            List<DeletePreparation> bindings
    ) {
    }

    private record DeletePreparation(
            UUID bindingId,
            GatewayProvider provider,
            String consumerExternalId,
            String externalId,
            UUID operationId
    ) {
    }

    private record ReconciliationPreparation(
            UUID bindingId,
            GatewayProvider provider,
            UUID operationId,
            GatewayCredentialInspectionSpec inspection,
            GatewayInspectionResult localResult
    ) {
    }

    private record ReconciliationAttempt(
            ReconciliationPreparation preparation,
            GatewayInspectionResult result,
            String failure
    ) {
    }
}

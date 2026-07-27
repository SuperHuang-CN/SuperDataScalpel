package cn.superhuang.data.scalpel.business.service.consumer.service;

import cn.superhuang.data.scalpel.business.service.consumer.domain.ApiConsumer;
import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerBinding;
import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerSyncStatus;
import cn.superhuang.data.scalpel.business.service.consumer.credential.repository.ApiConsumerCredentialRepository;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.repository.ApiServiceSubscriptionRepository;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerPortRegistry;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerReference;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerResult;
import cn.superhuang.data.scalpel.business.service.consumer.gateway.GatewayConsumerSpec;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.consumer.repository.ApiConsumerRepository;
import cn.superhuang.data.scalpel.business.service.consumer.repository.GatewayConsumerBindingRepository;
import cn.superhuang.data.scalpel.business.service.consumer.web.request.CreateApiConsumerRequest;
import cn.superhuang.data.scalpel.business.service.consumer.web.request.UpdateApiConsumerRequest;
import cn.superhuang.data.scalpel.business.service.consumer.web.response.ApiConsumerResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Owns API consumer data and coordinates it with provider-specific gateway bindings. */
@Service
public class ApiConsumerService {

    private static final Duration RECENT_OPERATION_TIMEOUT = Duration.ofSeconds(30);

    private final ApiConsumerRepository repository;
    private final GatewayConsumerBindingRepository bindingRepository;
    private final ApiConsumerCredentialRepository credentialRepository;
    private final ApiServiceSubscriptionRepository subscriptionRepository;
    private final GatewayConsumerPortRegistry portRegistry;
    private final SearchEngine searchEngine;
    private final TransactionTemplate transactionTemplate;

    public ApiConsumerService(
            ApiConsumerRepository repository,
            GatewayConsumerBindingRepository bindingRepository,
            ApiConsumerCredentialRepository credentialRepository,
            ApiServiceSubscriptionRepository subscriptionRepository,
            GatewayConsumerPortRegistry portRegistry,
            SearchEngine searchEngine,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.bindingRepository = bindingRepository;
        this.credentialRepository = credentialRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.portRegistry = portRegistry;
        this.searchEngine = searchEngine;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApiConsumerResponse> search(SearchRequest request) {
        Page<ApiConsumer> page = searchEngine.search(request, ApiConsumer.class, repository);
        Map<UUID, List<GatewayConsumerBinding>> bindings = bindingsByConsumerId(page.getContent());
        return new PageResponse<>(
                page.getContent().stream()
                        .map(consumer -> ApiConsumerResponse.from(
                                consumer,
                                bindings.getOrDefault(consumer.getId(), List.of())
                        ))
                        .toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public ApiConsumerResponse get(UUID id) {
        return response(requireConsumer(id));
    }

    public ApiConsumerResponse create(CreateApiConsumerRequest request) {
        GatewayProvider provider = portRegistry.activeProvider();
        SyncPreparation preparation;
        try {
            preparation = requireTransactionResult(transactionTemplate.execute(status -> {
                String code = ApiConsumer.normalizeCode(request.code());
                if (repository.existsByCode(code)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "消费者编码已存在");
                }
                ApiConsumer consumer = repository.saveAndFlush(
                        ApiConsumer.create(code, request.name(), request.description())
                );
                GatewayConsumerBinding binding = GatewayConsumerBinding.pending(consumer.getId(), provider);
                binding.beginSync();
                bindingRepository.saveAndFlush(binding);
                return preparation(consumer, binding);
            }));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "消费者编码已存在", exception);
        }
        return executeSync(preparation);
    }

    public ApiConsumerResponse update(UUID id, UpdateApiConsumerRequest request) {
        GatewayProvider provider = portRegistry.activeProvider();
        SyncPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            ApiConsumer consumer = requireConsumerForUpdate(id);
            assertNoRecentOperation(consumer.getId());
            consumer.update(request.name(), request.description());
            repository.saveAndFlush(consumer);
            GatewayConsumerBinding binding = requireOrCreateBinding(consumer.getId(), provider);
            binding.beginSync();
            bindingRepository.saveAndFlush(binding);
            return preparation(consumer, binding);
        }));
        return executeSync(preparation);
    }

    public ApiConsumerResponse sync(UUID id) {
        GatewayProvider provider = portRegistry.activeProvider();
        SyncPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            ApiConsumer consumer = requireConsumerForUpdate(id);
            assertNoRecentOperation(consumer.getId());
            GatewayConsumerBinding binding = requireOrCreateBinding(consumer.getId(), provider);
            binding.beginSync();
            bindingRepository.saveAndFlush(binding);
            return preparation(consumer, binding);
        }));
        return executeSync(preparation);
    }

    public ApiConsumerResponse reconcileGateway(UUID id) {
        List<ReconciliationPreparation> preparations = requireTransactionResult(
                transactionTemplate.execute(status -> prepareReconciliation(id))
        );
        List<ReconciliationAttempt> attempts = new ArrayList<>(preparations.size());
        for (ReconciliationPreparation preparation : preparations) {
            GatewayInspectionResult result = null;
            String failure = null;
            try {
                result = portRegistry.require(preparation.provider()).inspect(preparation.inspection());
                if (result == null) throw new IllegalStateException("网关未返回消费者对账结果");
            } catch (RuntimeException exception) {
                failure = safeMessage(exception);
            }
            attempts.add(new ReconciliationAttempt(preparation, result, failure));
        }
        transactionTemplate.executeWithoutResult(status -> completeReconciliation(attempts));
        return get(id);
    }

    public void delete(UUID id) {
        DeletePlan plan = requireTransactionResult(transactionTemplate.execute(status -> prepareDelete(id)));
        if (plan.consumerDeleted()) {
            return;
        }

        List<String> failures = new ArrayList<>();
        for (DeletePreparation preparation : plan.bindings()) {
            try {
                portRegistry.require(preparation.provider()).remove(new GatewayConsumerReference(
                        plan.consumerId(),
                        plan.code(),
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

        boolean consumerDeleted = requireTransactionResult(transactionTemplate.execute(
                status -> completeConsumerDelete(plan.consumerId())
        ));
        if (!failures.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "网关消费者删除失败：" + String.join("；", failures)
            );
        }
        if (!consumerDeleted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "消费者绑定状态已发生变化，请重新删除");
        }
    }

    private ApiConsumerResponse executeSync(SyncPreparation preparation) {
        String failure = null;
        String externalId = null;
        try {
            GatewayConsumerResult result = portRegistry.require(preparation.provider())
                    .upsert(preparation.spec());
            if (result == null || result.externalId() == null || result.externalId().isBlank()) {
                throw new IllegalStateException("网关未返回消费者外部 ID");
            }
            externalId = result.externalId();
        } catch (RuntimeException exception) {
            failure = safeMessage(exception);
        }
        String finalFailure = failure;
        String finalExternalId = externalId;
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeSync(preparation, finalExternalId, finalFailure)
        ));
    }

    private ApiConsumerResponse completeSync(
            SyncPreparation preparation,
            String externalId,
            String failure
    ) {
        ApiConsumer consumer = requireConsumerForUpdate(preparation.consumerId());
        GatewayConsumerBinding binding = bindingRepository.findByIdForUpdate(preparation.bindingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "消费者网关绑定不存在"));
        if (consumer.getRevision() == preparation.spec().revision()
                && binding.getSyncStatus() == GatewayConsumerSyncStatus.SYNC_PENDING
                && preparation.operationStartedAt().equals(binding.getOperationStartedAt())) {
            if (failure == null) {
                binding.synchronizedWith(externalId, preparation.spec().revision());
            } else {
                binding.syncFailed(failure);
            }
            bindingRepository.saveAndFlush(binding);
        }
        return response(consumer);
    }

    private DeletePlan prepareDelete(UUID id) {
        ApiConsumer consumer = requireConsumerForUpdate(id);
        if (subscriptionRepository.existsByConsumerId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "消费者仍有数据服务订阅，请先撤回订阅");
        }
        if (credentialRepository.existsByConsumerId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "消费者仍有 API Key，请先删除凭证");
        }
        List<GatewayConsumerBinding> bindings = bindingRepository.findAllByConsumerIdForUpdate(id);
        if (bindings.isEmpty()) {
            repository.delete(consumer);
            repository.flush();
            return new DeletePlan(id, consumer.getCode(), true, List.of());
        }
        assertNoRecentOperation(bindings);
        List<DeletePreparation> preparations = new ArrayList<>(bindings.size());
        for (GatewayConsumerBinding binding : bindings) {
            binding.beginDelete();
            bindingRepository.saveAndFlush(binding);
            preparations.add(new DeletePreparation(
                    binding.getId(),
                    binding.getProvider(),
                    binding.getExternalId(),
                    binding.getOperationStartedAt()
            ));
        }
        return new DeletePlan(id, consumer.getCode(), false, List.copyOf(preparations));
    }

    private List<ReconciliationPreparation> prepareReconciliation(UUID id) {
        ApiConsumer consumer = requireConsumerForUpdate(id);
        List<GatewayConsumerBinding> bindings = bindingRepository.findAllByConsumerIdForUpdate(id);
        if (bindings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "消费者尚无可对账的网关绑定");
        }
        assertNoRecentOperation(bindings);
        List<ReconciliationPreparation> preparations = new ArrayList<>(bindings.size());
        GatewayConsumerSpec expected = new GatewayConsumerSpec(
                consumer.getId(),
                consumer.getCode(),
                consumer.getName(),
                consumer.getDescription(),
                consumer.getRevision()
        );
        for (GatewayConsumerBinding binding : bindings) {
            UUID operationId = binding.beginReconciliation();
            bindingRepository.saveAndFlush(binding);
            preparations.add(new ReconciliationPreparation(
                    binding.getId(),
                    binding.getProvider(),
                    operationId,
                    new GatewayConsumerInspectionSpec(
                            expected,
                            new GatewayConsumerReference(
                                    consumer.getId(),
                                    consumer.getCode(),
                                    binding.getExternalId()
                            ),
                            binding.getSyncStatus() != GatewayConsumerSyncStatus.DELETE_PENDING
                                    && binding.getSyncStatus() != GatewayConsumerSyncStatus.DELETE_FAILED
                    )
            ));
        }
        return List.copyOf(preparations);
    }

    private void completeReconciliation(List<ReconciliationAttempt> attempts) {
        for (ReconciliationAttempt attempt : attempts) {
            GatewayConsumerBinding binding = bindingRepository
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
        GatewayConsumerBinding binding = bindingRepository.findByIdForUpdate(preparation.bindingId())
                .orElse(null);
        if (binding != null
                && binding.getSyncStatus() == GatewayConsumerSyncStatus.DELETE_PENDING
                && preparation.operationStartedAt().equals(binding.getOperationStartedAt())) {
            bindingRepository.delete(binding);
            bindingRepository.flush();
        }
    }

    private void failBindingDelete(DeletePreparation preparation, String failure) {
        GatewayConsumerBinding binding = bindingRepository.findByIdForUpdate(preparation.bindingId())
                .orElse(null);
        if (binding != null
                && binding.getSyncStatus() == GatewayConsumerSyncStatus.DELETE_PENDING
                && preparation.operationStartedAt().equals(binding.getOperationStartedAt())) {
            binding.deleteFailed(failure);
            bindingRepository.saveAndFlush(binding);
        }
    }

    private boolean completeConsumerDelete(UUID consumerId) {
        ApiConsumer consumer = repository.findByIdForUpdate(consumerId).orElse(null);
        if (consumer == null) {
            return true;
        }
        if (bindingRepository.existsByConsumerId(consumerId)) {
            return false;
        }
        repository.delete(consumer);
        repository.flush();
        return true;
    }

    private SyncPreparation preparation(
            ApiConsumer consumer,
            GatewayConsumerBinding binding
    ) {
        return new SyncPreparation(
                consumer.getId(),
                binding.getId(),
                binding.getProvider(),
                binding.getOperationStartedAt(),
                new GatewayConsumerSpec(
                        consumer.getId(),
                        consumer.getCode(),
                        consumer.getName(),
                        consumer.getDescription(),
                        consumer.getRevision()
                )
        );
    }

    private GatewayConsumerBinding requireOrCreateBinding(
            UUID consumerId,
            GatewayProvider provider
    ) {
        return bindingRepository.findByConsumerIdAndProviderForUpdate(consumerId, provider)
                .orElseGet(() -> GatewayConsumerBinding.pending(consumerId, provider));
    }

    private void assertNoRecentOperation(UUID consumerId) {
        assertNoRecentOperation(bindingRepository.findAllByConsumerId(consumerId));
    }

    private void assertNoRecentOperation(List<GatewayConsumerBinding> bindings) {
        Instant now = Instant.now();
        if (bindings.stream().anyMatch(binding -> binding.hasRecentOperation(now, RECENT_OPERATION_TIMEOUT))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "消费者正在执行网关操作，请稍后重试");
        }
    }

    private Map<UUID, List<GatewayConsumerBinding>> bindingsByConsumerId(
            Collection<ApiConsumer> consumers
    ) {
        if (consumers.isEmpty()) {
            return Map.of();
        }
        List<UUID> ids = consumers.stream().map(ApiConsumer::getId).toList();
        Map<UUID, List<GatewayConsumerBinding>> grouped = new HashMap<>();
        bindingRepository.findAllByConsumerIdIn(ids).forEach(binding ->
                grouped.computeIfAbsent(binding.getConsumerId(), ignored -> new ArrayList<>()).add(binding)
        );
        return grouped;
    }

    private ApiConsumerResponse response(ApiConsumer consumer) {
        return ApiConsumerResponse.from(
                consumer,
                bindingRepository.findAllByConsumerId(consumer.getId())
        );
    }

    private ApiConsumer requireConsumer(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消费者不存在"));
    }

    private ApiConsumer requireConsumerForUpdate(UUID id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消费者不存在"));
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception instanceof ResponseStatusException responseStatusException
                ? responseStatusException.getReason()
                : exception.getMessage();
        String normalized = message == null || message.isBlank() ? "远程调用失败" : message.trim();
        return normalized.substring(0, Math.min(1000, normalized.length()));
    }

    private static <T> T requireTransactionResult(T value) {
        if (value == null) {
            throw new IllegalStateException("事务未返回结果");
        }
        return value;
    }

    private record SyncPreparation(
            UUID consumerId,
            UUID bindingId,
            GatewayProvider provider,
            Instant operationStartedAt,
            GatewayConsumerSpec spec
    ) {
    }

    private record DeletePlan(
            UUID consumerId,
            String code,
            boolean consumerDeleted,
            List<DeletePreparation> bindings
    ) {
    }

    private record DeletePreparation(
            UUID bindingId,
            GatewayProvider provider,
            String externalId,
            Instant operationStartedAt
    ) {
    }

    private record ReconciliationPreparation(
            UUID bindingId,
            GatewayProvider provider,
            UUID operationId,
            GatewayConsumerInspectionSpec inspection
    ) {
    }

    private record ReconciliationAttempt(
            ReconciliationPreparation preparation,
            GatewayInspectionResult result,
            String failure
    ) {
    }
}

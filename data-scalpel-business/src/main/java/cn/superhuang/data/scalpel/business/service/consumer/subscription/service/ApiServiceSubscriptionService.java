package cn.superhuang.data.scalpel.business.service.consumer.subscription.service;

import cn.superhuang.data.scalpel.business.service.consumer.domain.ApiConsumer;
import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerBinding;
import cn.superhuang.data.scalpel.business.service.consumer.domain.GatewayConsumerSyncStatus;
import cn.superhuang.data.scalpel.business.service.consumer.repository.ApiConsumerRepository;
import cn.superhuang.data.scalpel.business.service.consumer.repository.GatewayConsumerBindingRepository;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.ApiServiceSubscription;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.ApiServiceSubscriptionDesiredState;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.GatewaySubscriptionBinding;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.domain.GatewaySubscriptionStatus;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionPortRegistry;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionInspectionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionReference;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionResult;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.gateway.GatewaySubscriptionSpec;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.repository.ApiServiceSubscriptionRepository;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.repository.GatewaySubscriptionBindingRepository;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.web.request.CreateApiServiceSubscriptionRequest;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.web.response.ApiServiceSubscriptionResponse;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServiceBinding;
import cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServicePublicationStatus;
import cn.superhuang.data.scalpel.business.service.gateway.repository.GatewayServiceBindingRepository;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayReconciliationReason;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ApiServiceSubscriptionService {

    private static final Duration RECENT_OPERATION_TIMEOUT = Duration.ofSeconds(30);

    private final ApiServiceSubscriptionRepository repository;
    private final GatewaySubscriptionBindingRepository bindingRepository;
    private final ApiConsumerRepository consumerRepository;
    private final GatewayConsumerBindingRepository consumerBindingRepository;
    private final DataServiceRepository dataServiceRepository;
    private final GatewayServiceBindingRepository serviceBindingRepository;
    private final GatewaySubscriptionPortRegistry portRegistry;
    private final SearchEngine searchEngine;
    private final TransactionTemplate transactionTemplate;

    public ApiServiceSubscriptionService(
            ApiServiceSubscriptionRepository repository,
            GatewaySubscriptionBindingRepository bindingRepository,
            ApiConsumerRepository consumerRepository,
            GatewayConsumerBindingRepository consumerBindingRepository,
            DataServiceRepository dataServiceRepository,
            GatewayServiceBindingRepository serviceBindingRepository,
            GatewaySubscriptionPortRegistry portRegistry,
            SearchEngine searchEngine,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.bindingRepository = bindingRepository;
        this.consumerRepository = consumerRepository;
        this.consumerBindingRepository = consumerBindingRepository;
        this.dataServiceRepository = dataServiceRepository;
        this.serviceBindingRepository = serviceBindingRepository;
        this.portRegistry = portRegistry;
        this.searchEngine = searchEngine;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApiServiceSubscriptionResponse> search(
            SearchRequest request,
            UUID consumerId,
            UUID dataServiceId
    ) {
        Specification<ApiServiceSubscription> fixed = Specification.unrestricted();
        if (consumerId != null) {
            fixed = fixed.and((root, query, builder) -> builder.equal(root.get("consumerId"), consumerId));
        }
        if (dataServiceId != null) {
            fixed = fixed.and((root, query, builder) -> builder.equal(root.get("dataServiceId"), dataServiceId));
        }
        Page<ApiServiceSubscription> page = searchEngine.search(
                request,
                ApiServiceSubscription.class,
                repository,
                fixed
        );
        List<ApiServiceSubscription> subscriptions = page.getContent();
        Map<UUID, ApiConsumer> consumers = consumerRepository.findAllById(
                subscriptions.stream().map(ApiServiceSubscription::getConsumerId).distinct().toList()
        ).stream().collect(Collectors.toMap(ApiConsumer::getId, Function.identity()));
        Map<UUID, DataService> services = dataServiceRepository.findAllById(
                subscriptions.stream().map(ApiServiceSubscription::getDataServiceId).distinct().toList()
        ).stream().collect(Collectors.toMap(DataService::getId, Function.identity()));
        Map<UUID, List<GatewaySubscriptionBinding>> bindings = bindingsBySubscriptionId(subscriptions);
        return new PageResponse<>(
                subscriptions.stream().map(subscription -> ApiServiceSubscriptionResponse.from(
                        subscription,
                        consumers.get(subscription.getConsumerId()),
                        services.get(subscription.getDataServiceId()),
                        bindings.getOrDefault(subscription.getId(), List.of())
                )).toList(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public ApiServiceSubscriptionResponse get(UUID id) {
        return response(requireSubscription(id));
    }

    public ApiServiceSubscriptionResponse create(CreateApiServiceSubscriptionRequest request) {
        GatewayProvider provider = portRegistry.activeProvider();
        GrantPreparation preparation;
        try {
            preparation = requireTransactionResult(transactionTemplate.execute(status -> {
                ApiConsumer consumer = requireConsumerForUpdate(request.consumerId());
                DataService dataService = requireDataServiceForUpdate(request.dataServiceId());
                validateGrantTarget(consumer, dataService, provider);
                if (repository.existsByConsumerIdAndDataServiceId(consumer.getId(), dataService.getId())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "消费者已经订阅该数据服务");
                }
                ApiServiceSubscription subscription = repository.saveAndFlush(
                        ApiServiceSubscription.create(consumer.getId(), dataService.getId())
                );
                GatewaySubscriptionBinding binding = GatewaySubscriptionBinding.pending(
                        subscription.getId(), provider
                );
                binding.beginGrant();
                bindingRepository.saveAndFlush(binding);
                return preparation(subscription, consumer, dataService, provider, binding);
            }));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "消费者已经订阅该数据服务", exception);
        }
        return executeGrant(preparation);
    }

    public ApiServiceSubscriptionResponse sync(UUID id) {
        GatewayProvider provider = portRegistry.activeProvider();
        GrantPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            ApiServiceSubscription subscription = requireSubscriptionForUpdate(id);
            if (subscription.getDesiredState() != ApiServiceSubscriptionDesiredState.GRANTED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "订阅正在撤回，不能重新授权");
            }
            assertNoRecentOperation(subscription.getId());
            ApiConsumer consumer = requireConsumerForUpdate(subscription.getConsumerId());
            DataService dataService = requireDataServiceForUpdate(subscription.getDataServiceId());
            validateGrantTarget(consumer, dataService, provider);
            GatewaySubscriptionBinding binding = bindingRepository
                    .findBySubscriptionIdAndProviderForUpdate(subscription.getId(), provider)
                    .orElseGet(() -> GatewaySubscriptionBinding.pending(subscription.getId(), provider));
            binding.beginGrant();
            bindingRepository.saveAndFlush(binding);
            return preparation(subscription, consumer, dataService, provider, binding);
        }));
        return executeGrant(preparation);
    }

    public ApiServiceSubscriptionResponse reconcileGateway(UUID id) {
        List<ReconciliationPreparation> preparations = requireTransactionResult(
                transactionTemplate.execute(status -> prepareReconciliation(id))
        );
        List<ReconciliationAttempt> attempts = new ArrayList<>(preparations.size());
        for (ReconciliationPreparation preparation : preparations) {
            GatewayInspectionResult result = preparation.localResult();
            String failure = null;
            if (result == null) {
                try {
                    result = portRegistry.require(preparation.provider()).inspect(preparation.inspection());
                    if (result == null) throw new IllegalStateException("网关未返回订阅对账结果");
                } catch (RuntimeException exception) {
                    failure = safeMessage(exception);
                }
            }
            attempts.add(new ReconciliationAttempt(preparation, result, failure));
        }
        transactionTemplate.executeWithoutResult(status -> completeReconciliation(attempts));
        return get(id);
    }

    public void revoke(UUID id) {
        RevokePlan plan = requireTransactionResult(transactionTemplate.execute(
                status -> prepareRevoke(id)
        ));
        if (plan.subscriptionDeleted()) return;

        List<String> failures = new ArrayList<>();
        for (RevokePreparation preparation : plan.bindings()) {
            try {
                portRegistry.require(preparation.provider()).revoke(new GatewaySubscriptionReference(
                        plan.subscriptionId(),
                        plan.consumerId(),
                        plan.consumerCode(),
                        preparation.consumerExternalId(),
                        plan.dataServiceId(),
                        plan.dataServiceCode(),
                        preparation.serviceExternalId(),
                        preparation.routeExternalId(),
                        preparation.externalMembershipId()
                ));
                transactionTemplate.executeWithoutResult(status -> completeBindingRevoke(preparation));
            } catch (RuntimeException exception) {
                String failure = safeMessage(exception);
                failures.add(preparation.provider() + "：" + failure);
                transactionTemplate.executeWithoutResult(
                        status -> failBindingRevoke(preparation, failure)
                );
            }
        }
        boolean deleted = requireTransactionResult(transactionTemplate.execute(
                status -> completeSubscriptionDelete(plan.subscriptionId())
        ));
        if (!failures.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "网关订阅撤回失败：" + String.join("；", failures)
            );
        }
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订阅绑定状态已发生变化，请重新撤回");
        }
    }

    private ApiServiceSubscriptionResponse executeGrant(GrantPreparation preparation) {
        String externalId = null;
        String failure = null;
        try {
            GatewaySubscriptionResult result = portRegistry.require(preparation.provider())
                    .grant(preparation.spec());
            if (result == null
                    || result.externalMembershipId() == null || result.externalMembershipId().isBlank()) {
                throw new IllegalStateException("网关未返回完整的订阅授权结果");
            }
            externalId = result.externalMembershipId();
        } catch (RuntimeException exception) {
            failure = safeMessage(exception);
        }
        String finalExternalId = externalId;
        String finalFailure = failure;
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeGrant(preparation, finalExternalId, finalFailure)
        ));
    }

    private ApiServiceSubscriptionResponse completeGrant(
            GrantPreparation preparation,
            String externalId,
            String failure
    ) {
        ApiServiceSubscription subscription = requireSubscriptionForUpdate(preparation.subscriptionId());
        GatewaySubscriptionBinding binding = bindingRepository.findByIdForUpdate(preparation.bindingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "订阅网关绑定不存在"));
        if (binding.getStatus() == GatewaySubscriptionStatus.GRANT_PENDING
                && preparation.operationId().equals(binding.getOperationId())) {
            if (failure == null) {
                binding.granted(externalId);
            } else {
                binding.grantFailed(failure);
            }
            bindingRepository.saveAndFlush(binding);
        }
        return response(subscription);
    }

    private GrantPreparation preparation(
            ApiServiceSubscription subscription,
            ApiConsumer consumer,
            DataService dataService,
            GatewayProvider provider,
            GatewaySubscriptionBinding binding
    ) {
        GatewayConsumerBinding consumerBinding = requireCurrentConsumerBinding(consumer, provider);
        GatewayServiceBinding serviceBinding = requireCurrentServiceBinding(dataService, provider);
        return new GrantPreparation(
                subscription.getId(),
                binding.getId(),
                provider,
                binding.getOperationId(),
                new GatewaySubscriptionSpec(
                        subscription.getId(),
                        consumer.getId(),
                        consumer.getCode(),
                        consumerBinding.getExternalId(),
                        dataService.getId(),
                        dataService.getCode(),
                        serviceBinding.getExternalServiceId(),
                        serviceBinding.getExternalRouteId()
                )
        );
    }

    private void validateGrantTarget(
            ApiConsumer consumer,
            DataService dataService,
            GatewayProvider provider
    ) {
        if (dataService.getAccessMode() != DataServiceAccessMode.SUBSCRIPTION_REQUIRED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "公开数据服务不需要订阅");
        }
        if (dataService.getStatus() != DataServiceStatus.ENABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已启用数据服务可以订阅");
        }
        requireCurrentConsumerBinding(consumer, provider);
        requireCurrentServiceBinding(dataService, provider);
    }

    private GatewayConsumerBinding requireCurrentConsumerBinding(ApiConsumer consumer, GatewayProvider provider) {
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

    private GatewayServiceBinding requireCurrentServiceBinding(DataService dataService, GatewayProvider provider) {
        GatewayServiceBinding binding = serviceBindingRepository
                .findByDataServiceIdAndProviderForUpdate(dataService.getId(), provider)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "数据服务尚未发布到当前网关"
                ));
        if (binding.getPublicationStatus() != GatewayServicePublicationStatus.PUBLISHED
                || binding.getPublishedRevision() != dataService.getRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务当前版本尚未发布到网关");
        }
        return binding;
    }

    private RevokePlan prepareRevoke(UUID id) {
        ApiServiceSubscription subscription = requireSubscriptionForUpdate(id);
        ApiConsumer consumer = requireConsumerForUpdate(subscription.getConsumerId());
        DataService dataService = requireDataServiceForUpdate(subscription.getDataServiceId());
        subscription.requestRevoke();
        repository.saveAndFlush(subscription);
        List<GatewaySubscriptionBinding> bindings = bindingRepository.findAllBySubscriptionId(id);
        if (bindings.isEmpty()) {
            repository.delete(subscription);
            repository.flush();
            return new RevokePlan(
                    id,
                    consumer.getId(),
                    consumer.getCode(),
                    dataService.getId(),
                    dataService.getCode(),
                    true,
                    List.of()
            );
        }
        assertNoRecentOperation(bindings);
        List<RevokePreparation> preparations = new ArrayList<>(bindings.size());
        for (GatewaySubscriptionBinding binding : bindings) {
            GatewayConsumerBinding consumerBinding = consumerBindingRepository
                    .findByConsumerIdAndProvider(consumer.getId(), binding.getProvider())
                    .orElse(null);
            GatewayServiceBinding serviceBinding = serviceBindingRepository
                    .findByDataServiceIdAndProviderForUpdate(dataService.getId(), binding.getProvider())
                    .orElse(null);
            binding.beginRevoke();
            bindingRepository.saveAndFlush(binding);
            preparations.add(new RevokePreparation(
                    binding.getId(),
                    binding.getProvider(),
                    consumerBinding == null ? consumer.getCode() : consumerBinding.getExternalId(),
                    binding.getExternalMembershipId(),
                    serviceBinding == null ? null : serviceBinding.getExternalServiceId(),
                    serviceBinding == null ? null : serviceBinding.getExternalRouteId(),
                    binding.getOperationId()
            ));
        }
        return new RevokePlan(
                id,
                consumer.getId(),
                consumer.getCode(),
                dataService.getId(),
                dataService.getCode(),
                false,
                List.copyOf(preparations)
        );
    }

    private List<ReconciliationPreparation> prepareReconciliation(UUID id) {
        ApiServiceSubscription subscription = requireSubscriptionForUpdate(id);
        ApiConsumer consumer = requireConsumerForUpdate(subscription.getConsumerId());
        DataService dataService = requireDataServiceForUpdate(subscription.getDataServiceId());
        List<GatewaySubscriptionBinding> bindings =
                bindingRepository.findAllBySubscriptionIdForUpdate(id);
        if (bindings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订阅尚无可对账的网关绑定");
        }
        assertNoRecentOperation(bindings);
        List<ReconciliationPreparation> preparations = new ArrayList<>(bindings.size());
        for (GatewaySubscriptionBinding binding : bindings) {
            GatewayConsumerBinding consumerBinding = consumerBindingRepository
                    .findByConsumerIdAndProvider(consumer.getId(), binding.getProvider())
                    .orElse(null);
            GatewayServiceBinding serviceBinding = serviceBindingRepository
                    .findByDataServiceIdAndProviderForUpdate(dataService.getId(), binding.getProvider())
                    .orElse(null);
            boolean missingDependency = consumerBinding == null
                    || consumerBinding.getExternalId() == null
                    || consumerBinding.getExternalId().isBlank()
                    || serviceBinding == null
                    || serviceBinding.getExternalServiceId() == null
                    || serviceBinding.getExternalServiceId().isBlank()
                    || serviceBinding.getExternalRouteId() == null
                    || serviceBinding.getExternalRouteId().isBlank();
            UUID operationId = binding.beginReconciliation();
            bindingRepository.saveAndFlush(binding);
            GatewayInspectionResult localResult = missingDependency
                    ? GatewayInspectionResult.drifted(
                            GatewayReconciliationReason.LOCAL_BINDING_MISSING,
                            "缺少同一网关的 Consumer 或数据服务绑定，无法可靠检查订阅授权"
                    )
                    : null;
            preparations.add(new ReconciliationPreparation(
                    binding.getId(),
                    binding.getProvider(),
                    operationId,
                    localResult == null ? new GatewaySubscriptionInspectionSpec(
                            new GatewaySubscriptionSpec(
                                    subscription.getId(),
                                    consumer.getId(),
                                    consumer.getCode(),
                                    consumerBinding.getExternalId(),
                                    dataService.getId(),
                                    dataService.getCode(),
                                    serviceBinding.getExternalServiceId(),
                                    serviceBinding.getExternalRouteId()
                            ),
                            binding.getExternalMembershipId(),
                            subscription.getDesiredState()
                                    == ApiServiceSubscriptionDesiredState.GRANTED
                    ) : null,
                    localResult
            ));
        }
        return List.copyOf(preparations);
    }

    private void completeReconciliation(List<ReconciliationAttempt> attempts) {
        for (ReconciliationAttempt attempt : attempts) {
            GatewaySubscriptionBinding binding = bindingRepository
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

    private void completeBindingRevoke(RevokePreparation preparation) {
        GatewaySubscriptionBinding binding = bindingRepository.findByIdForUpdate(preparation.bindingId()).orElse(null);
        if (binding != null
                && binding.getStatus() == GatewaySubscriptionStatus.REVOKE_PENDING
                && preparation.operationId().equals(binding.getOperationId())) {
            bindingRepository.delete(binding);
            bindingRepository.flush();
        }
    }

    private void failBindingRevoke(RevokePreparation preparation, String failure) {
        GatewaySubscriptionBinding binding = bindingRepository.findByIdForUpdate(preparation.bindingId()).orElse(null);
        if (binding != null
                && binding.getStatus() == GatewaySubscriptionStatus.REVOKE_PENDING
                && preparation.operationId().equals(binding.getOperationId())) {
            binding.revokeFailed(failure);
            bindingRepository.saveAndFlush(binding);
        }
    }

    private boolean completeSubscriptionDelete(UUID id) {
        ApiServiceSubscription subscription = repository.findByIdForUpdate(id).orElse(null);
        if (subscription == null) return true;
        if (bindingRepository.existsBySubscriptionId(id)) return false;
        repository.delete(subscription);
        repository.flush();
        return true;
    }

    private ApiServiceSubscriptionResponse response(ApiServiceSubscription subscription) {
        ApiConsumer consumer = consumerRepository.findById(subscription.getConsumerId()).orElse(null);
        DataService dataService = dataServiceRepository.findById(subscription.getDataServiceId()).orElse(null);
        return ApiServiceSubscriptionResponse.from(
                subscription,
                consumer,
                dataService,
                bindingRepository.findAllBySubscriptionId(subscription.getId())
        );
    }

    private Map<UUID, List<GatewaySubscriptionBinding>> bindingsBySubscriptionId(
            List<ApiServiceSubscription> subscriptions
    ) {
        if (subscriptions.isEmpty()) return Map.of();
        Map<UUID, List<GatewaySubscriptionBinding>> result = new HashMap<>();
        bindingRepository.findAllBySubscriptionIdIn(
                subscriptions.stream().map(ApiServiceSubscription::getId).toList()
        ).forEach(binding -> result.computeIfAbsent(
                binding.getSubscriptionId(), ignored -> new ArrayList<>()
        ).add(binding));
        return result;
    }

    private void assertNoRecentOperation(UUID subscriptionId) {
        assertNoRecentOperation(bindingRepository.findAllBySubscriptionId(subscriptionId));
    }

    private void assertNoRecentOperation(List<GatewaySubscriptionBinding> bindings) {
        Instant now = Instant.now();
        if (bindings.stream().anyMatch(binding -> binding.hasRecentOperation(now, RECENT_OPERATION_TIMEOUT))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "订阅正在执行网关操作，请稍后重试");
        }
    }

    private ApiServiceSubscription requireSubscription(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "服务订阅不存在"));
    }

    private ApiServiceSubscription requireSubscriptionForUpdate(UUID id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "服务订阅不存在"));
    }

    private ApiConsumer requireConsumerForUpdate(UUID id) {
        return consumerRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "消费者不存在"));
    }

    private DataService requireDataServiceForUpdate(UUID id) {
        return dataServiceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在"));
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception instanceof ResponseStatusException response
                ? response.getReason()
                : exception.getMessage();
        String normalized = message == null || message.isBlank() ? "远程调用失败" : message.trim();
        return normalized.substring(0, Math.min(1000, normalized.length()));
    }

    private static <T> T requireTransactionResult(T value) {
        if (value == null) throw new IllegalStateException("事务未返回订阅处理结果");
        return value;
    }

    private record GrantPreparation(
            UUID subscriptionId,
            UUID bindingId,
            GatewayProvider provider,
            UUID operationId,
            GatewaySubscriptionSpec spec
    ) {
    }

    private record RevokePlan(
            UUID subscriptionId,
            UUID consumerId,
            String consumerCode,
            UUID dataServiceId,
            String dataServiceCode,
            boolean subscriptionDeleted,
            List<RevokePreparation> bindings
    ) {
    }

    private record RevokePreparation(
            UUID bindingId,
            GatewayProvider provider,
            String consumerExternalId,
            String externalMembershipId,
            String serviceExternalId,
            String routeExternalId,
            UUID operationId
    ) {
    }

    private record ReconciliationPreparation(
            UUID bindingId,
            GatewayProvider provider,
            UUID operationId,
            GatewaySubscriptionInspectionSpec inspection,
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

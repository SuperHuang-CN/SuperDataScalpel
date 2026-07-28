package cn.superhuang.data.scalpel.business.service.gateway.service;

import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeployment;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeploymentStatus;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServiceBinding;
import cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServicePublicationStatus;
import cn.superhuang.data.scalpel.business.service.gateway.repository.GatewayServiceBindingRepository;
import cn.superhuang.data.scalpel.business.service.gateway.reconciliation.GatewayInspectionResult;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceDeploymentRepository;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates DataScalpel-owned service definitions with provider-specific gateway objects.
 * Remote gateway calls are intentionally outside management database transactions.
 */
@Service
public class DataServiceGatewayPublicationService {

    private static final Duration RECENT_OPERATION_TIMEOUT = Duration.ofSeconds(30);

    private final DataServiceRepository serviceRepository;
    private final DataServiceDeploymentRepository deploymentRepository;
    private final ServiceEngineRepository engineRepository;
    private final GatewayServiceBindingRepository bindingRepository;
    private final GatewayServicePortRegistry portRegistry;
    private final TransactionTemplate transactionTemplate;

    public DataServiceGatewayPublicationService(
            DataServiceRepository serviceRepository,
            DataServiceDeploymentRepository deploymentRepository,
            ServiceEngineRepository engineRepository,
            GatewayServiceBindingRepository bindingRepository,
            GatewayServicePortRegistry portRegistry,
            PlatformTransactionManager transactionManager
    ) {
        this.serviceRepository = serviceRepository;
        this.deploymentRepository = deploymentRepository;
        this.engineRepository = engineRepository;
        this.bindingRepository = bindingRepository;
        this.portRegistry = portRegistry;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void publish(UUID id) {
        GatewayProvider provider = portRegistry.activeProvider();
        PublishPreparation preparation = requireTransactionResult(transactionTemplate.execute(
                status -> preparePublish(id, provider)
        ));

        GatewayServiceResult result = null;
        String failure = null;
        try {
            result = portRegistry.require(preparation.provider()).publish(preparation.spec());
            requireResult(result);
        } catch (RuntimeException exception) {
            failure = safeMessage(exception);
        }

        GatewayServiceResult finalResult = result;
        String finalFailure = failure;
        transactionTemplate.executeWithoutResult(
                status -> completePublish(preparation, finalResult, finalFailure)
        );
    }

    public void reconcile(UUID id) {
        List<ReconciliationPreparation> preparations = requireTransactionResult(
                transactionTemplate.execute(status -> prepareReconciliation(id))
        );
        List<ReconciliationAttempt> attempts = new ArrayList<>(preparations.size());
        for (ReconciliationPreparation preparation : preparations) {
            GatewayInspectionResult result = null;
            String failure = null;
            try {
                result = portRegistry.require(preparation.provider()).inspect(preparation.inspection());
                if (result == null) throw new IllegalStateException("网关未返回数据服务对账结果");
            } catch (RuntimeException exception) {
                failure = safeMessage(exception);
            }
            attempts.add(new ReconciliationAttempt(
                    preparation,
                    result,
                    failure
            ));
        }
        transactionTemplate.executeWithoutResult(status -> completeReconciliation(attempts));
    }

    /**
     * Removes every gateway binding while keeping the current Engine deployment online.
     */
    public void unpublish(UUID id) {
        GatewayRemovalPlan plan = prepareGatewayRemovalPlan(id, "取消发布");
        List<GatewayRemovalAttempt> attempts = removeGatewayBindings(plan);
        transactionTemplate.executeWithoutResult(
                status -> completeGatewayRemoval(plan, attempts)
        );
    }

    /**
     * Removes every gateway binding and atomically transitions the Engine deployment to REMOVING.
     * Empty means at least one gateway object could not be removed and the Engine must stay online.
     */
    public Optional<EngineDisablePreparation> prepareDisable(UUID id) {
        GatewayRemovalPlan plan = prepareGatewayRemovalPlan(id, "停用");
        List<GatewayRemovalAttempt> attempts = removeGatewayBindings(plan);
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeGatewayRemovalAndBeginEngineDisable(plan, attempts)
        ));
    }

    private GatewayRemovalPlan prepareGatewayRemovalPlan(UUID id, String actionName) {
        return requireTransactionResult(transactionTemplate.execute(
                status -> prepareGatewayRemoval(id, actionName)
        ));
    }

    private List<GatewayRemovalAttempt> removeGatewayBindings(GatewayRemovalPlan plan) {
        List<GatewayRemovalAttempt> attempts = new ArrayList<>(plan.bindings().size());
        for (GatewayRemovalPreparation binding : plan.bindings()) {
            String failure = null;
            try {
                portRegistry.require(binding.provider()).remove(new GatewayServiceReference(
                        plan.serviceId(),
                        plan.serviceCode(),
                        binding.externalServiceId(),
                        binding.externalRouteId()
                ));
            } catch (RuntimeException exception) {
                failure = safeMessage(exception);
            }
            attempts.add(new GatewayRemovalAttempt(binding, failure));
        }
        return List.copyOf(attempts);
    }

    private PublishPreparation preparePublish(UUID id, GatewayProvider provider) {
        DataService service = requireServiceForUpdate(id);
        if (service.getStatus() != DataServiceStatus.ENABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已启用服务可以发布到网关");
        }
        DataServiceDeployment deployment = requireDeploymentForUpdate(id);
        if (deployment.getStatus() != DataServiceDeploymentStatus.DEPLOYED
                || deployment.getRevision() != service.getRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务当前版本尚未在 Engine 中就绪");
        }
        ServiceEngine engine = requireEngine(service.getEngineId());
        if (!engine.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎已停用");
        }

        List<GatewayServiceBinding> bindings = bindingRepository.findAllByDataServiceIdForUpdate(id);
        assertNoRecentOperation(bindings);
        GatewayServiceBinding binding = bindings.stream()
                .filter(candidate -> candidate.getProvider() == provider)
                .findFirst()
                .orElseGet(() -> GatewayServiceBinding.publishing(id, provider));
        binding.beginPublish();
        bindingRepository.saveAndFlush(binding);

        return new PublishPreparation(
                binding.getId(),
                provider,
                binding.getOperationStartedAt(),
                new GatewayServiceSpec(
                        service.getId(),
                        service.getCode(),
                        service.getName(),
                        service.getRevision(),
                        service.getRoutePath(),
                        engine.getPublicUrl(),
                        service.getAccessMode()
                )
        );
    }

    private void completePublish(
            PublishPreparation preparation,
            GatewayServiceResult result,
            String failure
    ) {
        DataService service = requireServiceForUpdate(preparation.spec().id());
        GatewayServiceBinding binding = bindingRepository.findByIdForUpdate(preparation.bindingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "数据服务网关绑定不存在"));
        if (service.getStatus() != DataServiceStatus.ENABLED
                || service.getRevision() != preparation.spec().revision()
                || binding.getPublicationStatus() != GatewayServicePublicationStatus.PUBLISHING
                || !preparation.operationStartedAt().equals(binding.getOperationStartedAt())) {
            return;
        }
        if (failure == null) {
            binding.publishedWith(
                    result.externalServiceId(),
                    result.externalRouteId(),
                    result.gatewayUrl(),
                    preparation.spec().revision()
            );
        } else {
            binding.publishFailed(failure);
        }
        bindingRepository.saveAndFlush(binding);
    }

    private GatewayRemovalPlan prepareGatewayRemoval(UUID id, String actionName) {
        DataService service = requireServiceForUpdate(id);
        if (service.getStatus() != DataServiceStatus.ENABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已启用服务可以" + actionName);
        }
        DataServiceDeployment deployment = requireDeploymentForUpdate(id);
        if (deployment.getRevision() != service.getRevision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务部署版本已发生变化");
        }
        List<GatewayServiceBinding> bindings = bindingRepository.findAllByDataServiceIdForUpdate(id);
        assertNoRecentOperation(bindings);
        List<GatewayRemovalPreparation> preparations = new ArrayList<>(bindings.size());
        for (GatewayServiceBinding binding : bindings) {
            binding.beginRemoval();
            bindingRepository.saveAndFlush(binding);
            preparations.add(new GatewayRemovalPreparation(
                    binding.getId(),
                    binding.getProvider(),
                    binding.getExternalServiceId(),
                    binding.getExternalRouteId(),
                    binding.getOperationStartedAt()
            ));
        }
        return new GatewayRemovalPlan(
                service.getId(),
                service.getCode(),
                service.getRevision(),
                List.copyOf(preparations)
        );
    }

    private List<ReconciliationPreparation> prepareReconciliation(UUID id) {
        DataService service = requireServiceForUpdate(id);
        ServiceEngine engine = requireEngine(service.getEngineId());
        List<GatewayServiceBinding> bindings = bindingRepository.findAllByDataServiceIdForUpdate(id);
        if (bindings.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务尚无可对账的网关绑定");
        }
        assertNoRecentOperation(bindings);
        List<ReconciliationPreparation> preparations = new ArrayList<>(bindings.size());
        for (GatewayServiceBinding binding : bindings) {
            UUID operationId = binding.beginReconciliation();
            bindingRepository.saveAndFlush(binding);
            GatewayServiceSpec expected = new GatewayServiceSpec(
                    service.getId(),
                    service.getCode(),
                    service.getName(),
                    service.getRevision(),
                    service.getRoutePath(),
                    engine.getPublicUrl(),
                    service.getAccessMode()
            );
            preparations.add(new ReconciliationPreparation(
                    binding.getId(),
                    binding.getProvider(),
                    operationId,
                    new GatewayServiceInspectionSpec(
                            expected,
                            new GatewayServiceReference(
                                    service.getId(),
                                    service.getCode(),
                                    binding.getExternalServiceId(),
                                    binding.getExternalRouteId()
                            ),
                            binding.getPublicationStatus() != GatewayServicePublicationStatus.REMOVING
                                    && binding.getPublicationStatus()
                                    != GatewayServicePublicationStatus.REMOVE_FAILED
                    )
            ));
        }
        return List.copyOf(preparations);
    }

    private void completeReconciliation(List<ReconciliationAttempt> attempts) {
        for (ReconciliationAttempt attempt : attempts) {
            GatewayServiceBinding binding = bindingRepository
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

    private Optional<EngineDisablePreparation> completeGatewayRemovalAndBeginEngineDisable(
            GatewayRemovalPlan plan,
            List<GatewayRemovalAttempt> attempts
    ) {
        if (!completeGatewayRemoval(plan, attempts)) {
            return Optional.empty();
        }

        DataServiceDeployment deployment = requireDeploymentForUpdate(plan.serviceId());
        ServiceEngine engine = requireEngine(deployment.getEngineId());
        deployment.beginRemoval();
        deploymentRepository.saveAndFlush(deployment);
        return Optional.of(new EngineDisablePreparation(
                plan.serviceId(),
                engine,
                plan.revision()
        ));
    }

    private boolean completeGatewayRemoval(
            GatewayRemovalPlan plan,
            List<GatewayRemovalAttempt> attempts
    ) {
        DataService service = requireServiceForUpdate(plan.serviceId());
        if (service.getStatus() != DataServiceStatus.ENABLED || service.getRevision() != plan.revision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务状态已发生变化");
        }
        DataServiceDeployment deployment = requireDeploymentForUpdate(plan.serviceId());
        if (deployment.getRevision() != plan.revision()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务部署版本已发生变化");
        }

        boolean failed = false;
        for (GatewayRemovalAttempt attempt : attempts) {
            GatewayServiceBinding binding = bindingRepository.findByIdForUpdate(attempt.preparation().bindingId())
                    .orElse(null);
            if (binding == null) {
                continue;
            }
            if (binding.getPublicationStatus() != GatewayServicePublicationStatus.REMOVING
                    || !attempt.preparation().operationStartedAt().equals(binding.getOperationStartedAt())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务网关绑定状态已发生变化");
            }
            if (attempt.failure() == null) {
                bindingRepository.delete(binding);
            } else {
                binding.removalFailed(attempt.failure());
                bindingRepository.save(binding);
                failed = true;
            }
        }
        bindingRepository.flush();
        if (failed || bindingRepository.existsByDataServiceId(plan.serviceId())) {
            return false;
        }
        return true;
    }

    private void assertNoRecentOperation(List<GatewayServiceBinding> bindings) {
        Instant now = Instant.now();
        if (bindings.stream().anyMatch(binding -> binding.hasRecentOperation(now, RECENT_OPERATION_TIMEOUT))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务正在执行网关操作，请稍后重试");
        }
    }

    private DataService requireServiceForUpdate(UUID id) {
        return serviceRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在"));
    }

    private DataServiceDeployment requireDeploymentForUpdate(UUID id) {
        return deploymentRepository.findByDataServiceIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "数据服务缺少部署状态"));
    }

    private ServiceEngine requireEngine(UUID id) {
        return engineRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎不存在"));
    }

    private static void requireResult(GatewayServiceResult result) {
        if (result == null
                || !hasText(result.externalServiceId())
                || !hasText(result.externalRouteId())
                || !hasText(result.gatewayUrl())) {
            throw new IllegalStateException("网关未返回完整的服务发布结果");
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception instanceof ResponseStatusException responseStatusException
                ? responseStatusException.getReason()
                : exception.getMessage();
        String normalized = hasText(message) ? message.trim() : "远程调用失败";
        return normalized.substring(0, Math.min(1000, normalized.length()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static <T> T requireTransactionResult(T value) {
        if (value == null) throw new IllegalStateException("事务未返回网关处理结果");
        return value;
    }

    private record PublishPreparation(
            UUID bindingId,
            GatewayProvider provider,
            Instant operationStartedAt,
            GatewayServiceSpec spec
    ) {
    }

    private record GatewayRemovalPlan(
            UUID serviceId,
            String serviceCode,
            long revision,
            List<GatewayRemovalPreparation> bindings
    ) {
    }

    private record GatewayRemovalPreparation(
            UUID bindingId,
            GatewayProvider provider,
            String externalServiceId,
            String externalRouteId,
            Instant operationStartedAt
    ) {
    }

    private record GatewayRemovalAttempt(
            GatewayRemovalPreparation preparation,
            String failure
    ) {
    }

    private record ReconciliationPreparation(
            UUID bindingId,
            GatewayProvider provider,
            UUID operationId,
            GatewayServiceInspectionSpec inspection
    ) {
    }

    private record ReconciliationAttempt(
            ReconciliationPreparation preparation,
            GatewayInspectionResult result,
            String failure
    ) {
    }

    public record EngineDisablePreparation(
            UUID serviceId,
            ServiceEngine engine,
            long revision
    ) {
    }
}

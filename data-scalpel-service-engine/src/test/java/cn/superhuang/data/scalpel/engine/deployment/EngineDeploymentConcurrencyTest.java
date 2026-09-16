package cn.superhuang.data.scalpel.engine.deployment;

import cn.superhuang.data.scalpel.contract.service.*;
import cn.superhuang.data.scalpel.engine.route.DynamicServiceRouteRegistry;
import cn.superhuang.data.scalpel.engine.script.EnginePublishedScriptService;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EngineDeploymentConcurrencyTest {
    @Test
    void earlierFailureCannotUnregisterLaterSuccessfulDeployment() throws Exception {
        var store = mock(EngineDeploymentStore.class);
        var routes = mock(DynamicServiceRouteRegistry.class);
        var service = new EngineRuntimeDeploymentService(store, mock(EngineDeploymentValidator.class),
                routes, mock(EnginePublishedScriptService.class));
        var request = mock(ServiceDeploymentRequest.class);
        var definition = mock(ServiceDefinitionSnapshot.class);
        UUID id = UUID.randomUUID();
        when(request.serviceId()).thenReturn(id);
        when(request.definition()).thenReturn(definition);
        when(definition.type()).thenReturn(DataServiceType.SQL_QUERY);
        var snapshot = new StoredServiceDeployment(request);
        when(store.beginDeployment(request)).thenReturn(new EngineDeploymentStore.DeploymentPreparation(snapshot, null));
        when(store.completeDeployment(id)).thenReturn(new ServiceDeploymentResponse(id, EngineDeploymentStatus.DEPLOYED, "ok"));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var secondStarted = new CountDownLatch(1);
        var calls = new AtomicInteger();
        doAnswer(call -> {
            if (calls.incrementAndGet() == 1) {
                entered.countDown();
                if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("release timed out");
                throw new IllegalStateException("first deployment failed");
            }
            return null;
        }).when(routes).register(snapshot);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> service.deploy(request));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            Future<ServiceDeploymentResponse> second = executor.submit(() -> {
                secondStarted.countDown();
                return service.deploy(request);
            });
            try {
                assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();
                assertThatThrownBy(() -> second.get(100, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            } finally {
                release.countDown();
            }
            assertThatThrownBy(() -> first.get(5, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class);
            assertThat(second.get(5, TimeUnit.SECONDS).status()).isEqualTo(EngineDeploymentStatus.DEPLOYED);
        }
        var order = inOrder(routes);
        order.verify(routes).register(snapshot);
        order.verify(routes).unregister(id);
        order.verify(routes).register(snapshot);
        verify(store, times(1)).completeDeployment(id);
    }

    @Test
    void startupRecoveryRechecksStateBeforeRegisteringOldSnapshot() {
        var store = mock(EngineDeploymentStore.class);
        var routes = mock(DynamicServiceRouteRegistry.class);
        var service = new EngineRuntimeDeploymentService(store, mock(EngineDeploymentValidator.class),
                routes, mock(EnginePublishedScriptService.class));
        var request = mock(ServiceDeploymentRequest.class);
        when(request.serviceId()).thenReturn(UUID.randomUUID());
        when(store.recoverableRemovals()).thenReturn(List.of());
        when(store.recoverableDeployments()).thenReturn(List.of(new StoredServiceDeployment(request)));
        when(store.recoverableDeployment(request.serviceId())).thenReturn(java.util.Optional.empty());
        service.restoreRoutes();
        verifyNoInteractions(routes);
    }
}

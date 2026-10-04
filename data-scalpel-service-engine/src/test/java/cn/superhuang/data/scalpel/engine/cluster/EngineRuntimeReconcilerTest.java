package cn.superhuang.data.scalpel.engine.cluster;

import cn.superhuang.data.scalpel.contract.service.*;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.engine.accesspolicy.EngineAccessPolicyService;
import cn.superhuang.data.scalpel.engine.datasource.EngineApiStudioDataSourceService;
import cn.superhuang.data.scalpel.engine.deployment.*;
import cn.superhuang.data.scalpel.engine.route.DynamicServiceRouteRegistry;
import cn.superhuang.data.scalpel.engine.script.EnginePublishedScriptService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.*;

class EngineRuntimeReconcilerTest {
    @Test
    void removesRetiredMvcRouteBeforeStudioReloadReusesItsPath() {
        var store = mock(EngineDeploymentStore.class);
        var routes = mock(DynamicServiceRouteRegistry.class);
        var scripts = mock(EnginePublishedScriptService.class);
        var reconciler = new EngineRuntimeReconciler(store, mock(EngineRuntimeDeploymentService.class),
                mock(EngineApiStudioDataSourceService.class), mock(EngineAccessPolicyService.class), routes, scripts);
        var request = new ServiceDeploymentRequest(UUID.randomUUID(), "old", "/open-api/v1/reuse", "old",
                ServiceDefinitionSnapshot.standard(new StandardServiceDefinition(1, null, "public", "sample",
                        List.of(new ServiceFieldDefinition("id", "id", PlatformDataType.INTEGER, false, true)))),
                UUID.randomUUID());
        when(store.all()).thenReturn(List.of(new StoredServiceDeployment(request, 1, EngineDeploymentRecordStatus.DEPLOYED)));
        reconciler.reconcile();
        clearInvocations(routes, scripts);
        var replacement = new StoredServiceDeployment(new ServiceDeploymentRequest(UUID.randomUUID(), "new",
                request.routePath(), "new", ServiceDefinitionSnapshot.script(new ScriptServiceDefinition("return 1")),
                request.dataSourceId()), 1, EngineDeploymentRecordStatus.DEPLOYED);
        when(store.all()).thenReturn(List.of(replacement,
                new StoredServiceDeployment(request, 2, EngineDeploymentRecordStatus.REMOVED)));
        reconciler.reconcile();
        int firstRemoval = mockingDetails(routes).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("unregister")
                        && request.serviceId().equals(call.getArgument(0)))
                .mapToInt(org.mockito.invocation.Invocation::getSequenceNumber).min().orElseThrow();
        int studioReload = mockingDetails(scripts).getInvocations().stream()
                .filter(call -> call.getMethod().getName().equals("reloadStudioRoutes"))
                .mapToInt(org.mockito.invocation.Invocation::getSequenceNumber).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(firstRemoval < studioReload);
        verify(scripts).installRuntime(replacement);
    }
}

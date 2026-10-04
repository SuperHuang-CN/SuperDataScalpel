package cn.superhuang.data.scalpel.engine.cluster;

import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import cn.superhuang.data.scalpel.engine.accesspolicy.EngineAccessPolicyService;
import cn.superhuang.data.scalpel.engine.datasource.EngineApiStudioDataSourceService;
import cn.superhuang.data.scalpel.engine.deployment.*;
import cn.superhuang.data.scalpel.engine.route.DynamicServiceRouteRegistry;
import cn.superhuang.data.scalpel.engine.script.EnginePublishedScriptService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import java.util.*;

/** Executed on the bounded control worker or a serialized management command, never a data request. */
@Component
@ConditionalOnProperty(name="data-scalpel.engine.cluster.enabled",matchIfMissing=true)
public class EngineRuntimeReconciler {
    private final EngineDeploymentStore store;
    private final EngineRuntimeDeploymentService deployments;
    private final EngineApiStudioDataSourceService dataSources;
    private final EngineAccessPolicyService policy;
    private final DynamicServiceRouteRegistry routes;
    private final EnginePublishedScriptService scripts;
    private final Map<UUID,Long> installed=new HashMap<>();
    public EngineRuntimeReconciler(EngineDeploymentStore store,EngineRuntimeDeploymentService deployments,
            EngineApiStudioDataSourceService dataSources,EngineAccessPolicyService policy,
            DynamicServiceRouteRegistry routes,EnginePublishedScriptService scripts) {
        this.store=store;this.deployments=deployments;this.dataSources=dataSources;this.policy=policy;this.routes=routes;this.scripts=scripts;
    }
    public void reconcile() {
        dataSources.synchronizeRuntime();
        var current=store.all();
        var next=new HashMap<UUID,Long>();
        // Remove changed/retired mappings first: another service may now legitimately own an old path.
        var desired=new HashMap<UUID,StoredServiceDeployment>();
        for(var snapshot:current) desired.put(snapshot.request().serviceId(),snapshot);
        for(var entry:installed.entrySet()) {
            var snapshot=desired.get(entry.getKey());
            if(snapshot==null || snapshot.status()!=EngineDeploymentRecordStatus.DEPLOYED
                    || snapshot.generation()!=entry.getValue()) {
                routes.unregister(entry.getKey());scripts.removeRuntime(entry.getKey());
            }
        }
        for(var snapshot:current) {
            if(snapshot.status()!=EngineDeploymentRecordStatus.DEPLOYED) {
                routes.unregister(snapshot.request().serviceId());
                scripts.removeRuntime(snapshot.request().serviceId());
            }
        }
        // Studio must not publish a newly reused path while our retired MVC mapping still occupies it.
        deployments.recoverInterruptedOperations();
        scripts.reloadStudioRoutes();
        current=store.all();
        for(var snapshot:current) {
            UUID id=snapshot.request().serviceId();
            if(snapshot.status()!=EngineDeploymentRecordStatus.DEPLOYED) {
                routes.unregister(id);scripts.removeRuntime(id);continue;
            }
            if(snapshot.request().definition().type()==DataServiceType.SCRIPT_API) {
                scripts.installRuntime(snapshot);
            } else if(!Objects.equals(installed.get(id),snapshot.generation())) {
                routes.register(snapshot);
            }
            next.put(id,snapshot.generation());
        }
        for(UUID id:installed.keySet())if(!next.containsKey(id)){routes.unregister(id);scripts.removeRuntime(id);}
        policy.restoreAppliedPolicy();
        installed.clear();installed.putAll(next);
    }
}

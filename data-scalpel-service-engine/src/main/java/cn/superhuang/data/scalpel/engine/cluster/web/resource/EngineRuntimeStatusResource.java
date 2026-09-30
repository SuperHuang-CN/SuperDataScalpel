package cn.superhuang.data.scalpel.engine.cluster.web.resource;

import cn.superhuang.data.scalpel.engine.cluster.EngineClusterCoordinator;
import cn.superhuang.data.scalpel.engine.cluster.web.response.EngineRuntimeStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/runtime")
@Tag(name="服务引擎运行状态",description="管理 Token 保护的当前节点同步状态，不聚合其他节点")
public class EngineRuntimeStatusResource {
    private final EngineClusterCoordinator coordinator;
    public EngineRuntimeStatusResource(ObjectProvider<EngineClusterCoordinator> coordinator){this.coordinator=coordinator.getIfAvailable();}
    @GetMapping
    @Operation(summary="查询当前 Engine 节点的配置就绪状态",description="只读内存快照，不查询业务库。需有效管理 Token；用于识别副本及排查版本滞后。流量入口应使用 /actuator/health/readiness 摘除未就绪节点，不应使用存活检查代替就绪检查。")
    public EngineRuntimeStatusResponse get() {
        if(coordinator==null)return new EngineRuntimeStatusResponse(false,null,true,-1,-1,null,null,0,0);
        var s=coordinator.snapshot();return new EngineRuntimeStatusResponse(true,s.instanceId(),s.ready(),s.loadedRevision(),
                s.targetRevision(),s.lastConfirmedAt(),s.error(),s.pollIntervalMs(),s.maxStaleMs());
    }
}

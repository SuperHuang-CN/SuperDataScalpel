package cn.superhuang.data.scalpel.business.compute.web.resource;

import cn.superhuang.data.scalpel.business.compute.service.ComputeEngineDiscoveryService;
import cn.superhuang.data.scalpel.business.compute.web.request.DiscoverComputeTargetsRequest;
import cn.superhuang.data.scalpel.business.compute.web.request.RegisterComputeTargetsRequest;
import cn.superhuang.data.scalpel.business.compute.web.response.ComputeTargetDiscoveryResponse;
import cn.superhuang.data.scalpel.business.compute.web.response.RegisterComputeTargetsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "计算引擎")
@RestController
@RequestMapping("/api/v1/compute-engines/actions")
public class ComputeEngineDiscoveryResource {
    private final ComputeEngineDiscoveryService service;
    public ComputeEngineDiscoveryResource(ComputeEngineDiscoveryService service) { this.service = service; }

    @PostMapping("/query-targets")
    @PreAuthorize("hasAuthority('compute.engine.create') and hasAuthority('compute.engine.test')")
    @Operation(summary = "发现 Dispatcher 执行目标", description = "只读查询。通过请求体传递敏感 Token，连接 Dispatcher 并返回已启用目标、就绪状态及本平台已有记录。不保存 Token，不创建或注册引擎。认证失败、不可达或发现协议不兼容返回 502，地址格式不合法返回 400。")
    public ComputeTargetDiscoveryResponse discover(@Valid @RequestBody DiscoverComputeTargetsRequest request) {
        return service.discover(request);
    }

    @PostMapping("/register-targets")
    @PreAuthorize("hasAuthority('compute.engine.create') and hasAuthority('compute.engine.manage')")
    @Operation(summary = "批量注册发现的执行目标", description = "重新确认 Dispatcher 身份和目标摘要，为每个勾选目标创建或重用一条引擎。协议 v3 使用发现的实例共享命令/Runner Topic，并要求 Admin 事件 Topic 已被本平台监听；旧协议 v2 保留独占通道兼容。逐项激活后返回 200 和各项结果，部分失败不回滚成功项，失败记录保留供重试。已有配置不覆盖，旧通道不可通过重复注册直接迁移。实例身份改变整批返回 409；目标被占用、名称冲突或目标未就绪记录到对应失败项。")
    public RegisterComputeTargetsResponse register(@Valid @RequestBody RegisterComputeTargetsRequest request) {
        return service.register(request);
    }
}

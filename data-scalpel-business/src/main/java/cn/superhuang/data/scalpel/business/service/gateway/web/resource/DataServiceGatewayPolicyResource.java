package cn.superhuang.data.scalpel.business.service.gateway.web.resource;

import cn.superhuang.data.scalpel.business.service.gateway.datascalpel.DataServiceGatewayPolicyService;
import cn.superhuang.data.scalpel.business.service.gateway.web.request.GatewayTrafficPolicyRequest;
import cn.superhuang.data.scalpel.business.service.gateway.web.response.GatewayTrafficPolicyResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/data-services/{id}")
@Tag(name = "数据服务-网关保护策略")
public class DataServiceGatewayPolicyResource {
    private final DataServiceGatewayPolicyService service;
    public DataServiceGatewayPolicyResource(DataServiceGatewayPolicyService service) { this.service = service; }
    @GetMapping("/gateway-traffic-policy")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "读取网关保护策略", description = "通过公开 HTTP 读取自研网关持久化策略及节点修订，不改变发布状态。未发布返回 409，不支持的网关返回 501，通信失败返回 502。")
    public GatewayTrafficPolicyResponse get(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id) {
        return service.get(id);
    }
    @PostMapping("/actions/update-gateway-traffic-policy")
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "保存网关保护策略", description = "更新已发布服务的单节点限流、并发、请求体和 IP 策略；不修改服务定义及发布流程。节点异步加载，应核对 loadedRevision。无效请求返回 400，未发布 409，未适配 501，远端未确认 502。")
    public GatewayTrafficPolicyResponse update(@Parameter(description = "数据服务 UUID。") @PathVariable UUID id,
                                               @Valid @RequestBody GatewayTrafficPolicyRequest request) {
        return service.update(id, request);
    }
}

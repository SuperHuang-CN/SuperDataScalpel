package cn.superhuang.data.scalpel.business.service.gateway.web.resource;

import cn.superhuang.data.scalpel.business.service.gateway.datascalpel.DataServiceGatewayValidityService;
import cn.superhuang.data.scalpel.business.service.gateway.web.request.GatewayAccessValidityRequest;
import cn.superhuang.data.scalpel.business.service.gateway.web.response.GatewayAccessValidityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gateway-access-validities/{kind}/{id}")
@Tag(name = "数据服务-网关有效期")
public class GatewayAccessValidityResource {
    private final DataServiceGatewayValidityService service;
    public GatewayAccessValidityResource(DataServiceGatewayValidityService service) { this.service = service; }
    @GetMapping
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询凭证或订阅有效期", description = "只读查询自研网关对象有效期及加载修订；对象不存在 404，未同步 409，未适配 501，远端失败 502。")
    public GatewayAccessValidityResponse get(@Parameter(description = "对象类型：keys 为 API Key，subscriptions 为服务订阅。") @PathVariable String kind,
            @Parameter(description = "DataScalpel 凭证或订阅 UUID，不是网关内部 UUID。") @PathVariable UUID id) {
        return service.access(kind, id, null);
    }
    @PostMapping("/actions/update")
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "修改或续期凭证与订阅", description = "同步保存到自研网关，节点异步加载；不重发密钥、不自动授权已撤回订阅。空到期时间取消自动到期。非法时间 400，未同步 409，未适配 501，结果未确认 502。")
    public GatewayAccessValidityResponse update(@Parameter(description = "对象类型：keys 或 subscriptions。") @PathVariable String kind,
            @Parameter(description = "DataScalpel 凭证或订阅 UUID。") @PathVariable UUID id,
            @Valid @RequestBody GatewayAccessValidityRequest request) {
        return service.access(kind, id, request);
    }
}

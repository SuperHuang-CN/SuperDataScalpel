package cn.superhuang.data.scalpel.business.service.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.ServiceEngineAccessPolicyService;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateServiceEngineAccessPolicyRequest;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineAccessPolicyResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@io.swagger.v3.oas.annotations.tags.Tag(name = "服务引擎访问策略")
@RestController
@RequestMapping("/api/v1/service-engines/{engineId}")
public class ServiceEngineAccessPolicyResource {

    private final ServiceEngineAccessPolicyService service;

    public ServiceEngineAccessPolicyResource(ServiceEngineAccessPolicyService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "服务引擎访问策略：查看详情")
    @Operation(summary = "查看服务引擎访问策略", description = "返回管理端期望的来源 IP/CIDR 白名单、最近一次远端应用结果和同步状态；尚未配置时返回空策略。仅 DataScalpel Service Engine 支持该能力。")
    @GetMapping("/access-policy")
    @PreAuthorize("hasAuthority('service.engine.view')")
    public ServiceEngineAccessPolicyResponse get(@Parameter(description = "DataScalpel Service Engine UUID。") @PathVariable UUID engineId) {
        return service.get(engineId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "服务引擎访问策略：修改")
    @Operation(summary = "修改服务引擎访问策略", description = "要求 DataScalpel 引擎已启用；规范化并整体保存新的业务访问 IP/CIDR 策略，每次调用递增 desiredRevision，然后立即下发远端。失败时仍保留期望配置并写入 FAILED 状态，但当前调用返回 502；随后可查询状态或重新同步。")
    @PostMapping("/actions/update-access-policy")
    @PreAuthorize("hasAuthority('service.engine.update')")
    public ServiceEngineAccessPolicyResponse update(
            @Parameter(description = "DataScalpel Service Engine UUID。") @PathVariable UUID engineId,
            @Valid @RequestBody UpdateServiceEngineAccessPolicyRequest request
    ) {
        return service.update(engineId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "服务引擎访问策略：同步")
    @Operation(summary = "重新同步服务引擎访问策略", description = "要求 DataScalpel 引擎已启用且已有非空允许列表；把当前 desiredRevision 和规则重新下发，不递增修订。远端不可达、拒绝或未确认同一修订时保存 FAILED 状态后返回 502。")
    @PostMapping("/actions/sync-access-policy")
    @PreAuthorize("hasAuthority('service.engine.update')")
    public ServiceEngineAccessPolicyResponse sync(@Parameter(description = "DataScalpel Service Engine UUID。") @PathVariable UUID engineId) {
        return service.sync(engineId);
    }
}

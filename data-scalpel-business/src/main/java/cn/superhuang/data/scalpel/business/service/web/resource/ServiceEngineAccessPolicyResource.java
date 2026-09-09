package cn.superhuang.data.scalpel.business.service.web.resource;

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
    @GetMapping("/access-policy")
    @PreAuthorize("hasAuthority('service.engine.view')")
    public ServiceEngineAccessPolicyResponse get(@PathVariable UUID engineId) {
        return service.get(engineId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "服务引擎访问策略：修改")
    @PostMapping("/actions/update-access-policy")
    @PreAuthorize("hasAuthority('service.engine.update')")
    public ServiceEngineAccessPolicyResponse update(
            @PathVariable UUID engineId,
            @Valid @RequestBody UpdateServiceEngineAccessPolicyRequest request
    ) {
        return service.update(engineId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "服务引擎访问策略：同步")
    @PostMapping("/actions/sync-access-policy")
    @PreAuthorize("hasAuthority('service.engine.update')")
    public ServiceEngineAccessPolicyResponse sync(@PathVariable UUID engineId) {
        return service.sync(engineId);
    }
}

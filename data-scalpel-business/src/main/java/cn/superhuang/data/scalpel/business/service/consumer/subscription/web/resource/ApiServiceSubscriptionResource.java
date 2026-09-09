package cn.superhuang.data.scalpel.business.service.consumer.subscription.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.service.ApiServiceSubscriptionService;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.web.request.CreateApiServiceSubscriptionRequest;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.web.response.ApiServiceSubscriptionResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-service-subscriptions")
@Tag(name = "数据服务-API 服务订阅")
public class ApiServiceSubscriptionResource {

    private final ApiServiceSubscriptionService service;

    public ApiServiceSubscriptionResource(ApiServiceSubscriptionService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 API 服务订阅")
    @GetMapping
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询 API 服务订阅")
    public PageResponse<ApiServiceSubscriptionResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request,
            @RequestParam(required = false) UUID consumerId,
            @RequestParam(required = false) UUID dataServiceId
    ) {
        return service.search(request, consumerId, dataServiceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 API 服务订阅详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询 API 服务订阅详情")
    public ApiServiceSubscriptionResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "创建订阅并同步网关授权")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "创建订阅并同步网关授权")
    public ApiServiceSubscriptionResponse create(
            @Valid @RequestBody CreateApiServiceSubscriptionRequest request
    ) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "重新同步订阅授权")
    @PostMapping("/{id}/actions/sync")
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "重新同步订阅授权")
    public ApiServiceSubscriptionResponse sync(@PathVariable UUID id) {
        return service.sync(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "立即对账订阅授权网关状态")
    @PostMapping("/{id}/actions/reconcile-gateway")
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "立即对账订阅授权网关状态")
    public ApiServiceSubscriptionResponse reconcileGateway(@PathVariable UUID id) {
        return service.reconcileGateway(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "撤回网关授权并删除订阅")
    @PostMapping("/{id}/actions/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "撤回网关授权并删除订阅")
    public void revoke(@PathVariable UUID id) {
        service.revoke(id);
    }
}

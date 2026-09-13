package cn.superhuang.data.scalpel.business.service.consumer.subscription.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.service.ApiServiceSubscriptionService;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.web.request.CreateApiServiceSubscriptionRequest;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.web.response.ApiServiceSubscriptionResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "查询 API 服务订阅", description = "按通用 Search DSL 分页查询消费者与数据服务的订阅，可按消费者或服务缩小范围，并返回各网关授权状态；不会实时访问网关。")
    public PageResponse<ApiServiceSubscriptionResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request,
            @Parameter(description = "API 调用方 UUID。") @RequestParam(required = false) UUID consumerId,
            @Parameter(description = "数据服务 UUID。") @RequestParam(required = false) UUID dataServiceId
    ) {
        return service.search(request, consumerId, dataServiceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 API 服务订阅详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询 API 服务订阅详情", description = "返回订阅关联的消费者、数据服务以及全部网关成员授权状态和最近错误。")
    public ApiServiceSubscriptionResponse get(@Parameter(description = "API 服务订阅 UUID。") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "创建订阅并同步网关授权")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "创建订阅并同步网关授权", description = "要求服务 ENABLED、当前 revision 已按 SUBSCRIPTION_REQUIRED 发布到活动网关，且消费者当前 revision 已同步到同一网关；随后保存唯一的消费者-服务订阅并建立远端成员关系。远端授权失败仍返回 201，保留本地订阅并记录 GRANT_FAILED；调用方必须检查 gatewayBindings。")
    public ApiServiceSubscriptionResponse create(
            @Valid @RequestBody CreateApiServiceSubscriptionRequest request
    ) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "重新同步订阅授权")
    @PostMapping("/{id}/actions/sync")
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "重新同步订阅授权", description = "要求订阅期望仍为 GRANTED，并重新校验消费者和服务当前版本在活动网关已同步，再授予远端成员关系；不改变订阅主数据。远端失败仍返回 200 并记录 GRANT_FAILED；调用方必须检查 gatewayBindings。")
    public ApiServiceSubscriptionResponse sync(@Parameter(description = "API 服务订阅 UUID。") @PathVariable UUID id) {
        return service.sync(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "立即对账订阅授权网关状态")
    @PostMapping("/{id}/actions/reconcile-gateway")
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "立即对账订阅授权网关状态", description = "检查订阅在所有已知网关中的成员关系是否按 desiredState 存在且归属一致，并更新漂移与错误状态；缺少同网关消费者或服务绑定时直接记录本地漂移，不访问该网关，也不会自动重新授权。单个网关检查失败仍返回 200 并记录 CHECK_FAILED。")
    public ApiServiceSubscriptionResponse reconcileGateway(@Parameter(description = "API 服务订阅 UUID。") @PathVariable UUID id) {
        return service.reconcileGateway(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "撤回网关授权并删除订阅")
    @PostMapping("/{id}/actions/revoke")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "撤回网关授权并删除订阅", description = "先从所有网关撤回消费者对服务的调用权限，再删除本地订阅。远端撤回失败时保留订阅和失败状态。")
    public void revoke(@Parameter(description = "API 服务订阅 UUID。") @PathVariable UUID id) {
        service.revoke(id);
    }
}

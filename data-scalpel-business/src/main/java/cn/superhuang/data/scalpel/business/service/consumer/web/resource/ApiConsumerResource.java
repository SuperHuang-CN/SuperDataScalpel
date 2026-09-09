package cn.superhuang.data.scalpel.business.service.consumer.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.consumer.service.ApiConsumerService;
import cn.superhuang.data.scalpel.business.service.consumer.web.request.CreateApiConsumerRequest;
import cn.superhuang.data.scalpel.business.service.consumer.web.request.UpdateApiConsumerRequest;
import cn.superhuang.data.scalpel.business.service.consumer.web.response.ApiConsumerResponse;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-consumers")
@Tag(name = "数据服务-API 消费者")
public class ApiConsumerResource {

    private final ApiConsumerService service;

    public ApiConsumerResource(ApiConsumerService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 API 消费者")
    @GetMapping
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询 API 消费者")
    public PageResponse<ApiConsumerResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 API 消费者详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询 API 消费者详情")
    public ApiConsumerResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "创建并同步 API 消费者")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.update')")
    @Operation(summary = "创建并同步 API 消费者")
    public ApiConsumerResponse create(
            @Valid @RequestBody CreateApiConsumerRequest request
    ) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改并同步 API 消费者")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('service.update')")
    @Operation(summary = "修改并同步 API 消费者")
    public ApiConsumerResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateApiConsumerRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "重新同步 API 消费者")
    @PostMapping("/{id}/actions/sync")
    @PreAuthorize("hasAuthority('service.update')")
    @Operation(summary = "重新同步 API 消费者")
    public ApiConsumerResponse sync(@PathVariable UUID id) {
        return service.sync(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "立即对账 API 消费者网关状态")
    @PostMapping("/{id}/actions/reconcile-gateway")
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "立即对账 API 消费者网关状态")
    public ApiConsumerResponse reconcileGateway(@PathVariable UUID id) {
        return service.reconcileGateway(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "从网关和 DataScalpel 删除 API 消费者")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.update')")
    @Operation(summary = "从网关和 DataScalpel 删除 API 消费者")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}

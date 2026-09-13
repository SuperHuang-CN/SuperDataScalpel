package cn.superhuang.data.scalpel.business.service.consumer.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.consumer.service.ApiConsumerService;
import cn.superhuang.data.scalpel.business.service.consumer.web.request.CreateApiConsumerRequest;
import cn.superhuang.data.scalpel.business.service.consumer.web.request.UpdateApiConsumerRequest;
import cn.superhuang.data.scalpel.business.service.consumer.web.response.ApiConsumerResponse;
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
    @Operation(summary = "查询 API 消费者", description = "按通用 Search DSL 分页查询消费者，并附带各网关提供方的同步、删除和对账状态；不会实时访问网关。")
    public PageResponse<ApiConsumerResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 API 消费者详情")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询 API 消费者详情", description = "返回消费者基本信息、修订号以及全部网关绑定状态和最近错误；不返回任何 API Key 明文或摘要。")
    public ApiConsumerResponse get(@Parameter(description = "API 消费者 UUID。") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "创建并同步 API 消费者")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.update')")
    @Operation(summary = "创建并同步 API 消费者", description = "保存唯一消费者编码并同步到当前活动 API Gateway。网关成功时返回 201 和 SYNCED 绑定；远端失败仍返回 201，保留本地消费者并记录 SYNC_FAILED，调用方必须检查 gatewayBindings，之后可重新同步或对账。")
    public ApiConsumerResponse create(
            @Valid @RequestBody CreateApiConsumerRequest request
    ) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改并同步 API 消费者")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('service.update')")
    @Operation(summary = "修改并同步 API 消费者", description = "整体修改消费者名称和说明并无条件递增修订号，再覆盖同步到当前活动网关；重复提交相同内容也会产生新 revision，消费者编码不可修改。远端失败仍返回 200 并记录 SYNC_FAILED；调用方必须检查 gatewayBindings。30 秒内已有同步、删除或对账操作时拒绝执行。")
    public ApiConsumerResponse update(
            @Parameter(description = "API 消费者 UUID。") @PathVariable UUID id,
            @Valid @RequestBody UpdateApiConsumerRequest request
    ) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "重新同步 API 消费者")
    @PostMapping("/{id}/actions/sync")
    @PreAuthorize("hasAuthority('service.update')")
    @Operation(summary = "重新同步 API 消费者", description = "把当前保存的消费者定义重新写入活动网关，不修改本地业务字段或 revision；用于修复同步失败或网关配置丢失。远端失败仍返回 200 并记录 SYNC_FAILED；调用方必须检查 gatewayBindings。")
    public ApiConsumerResponse sync(@Parameter(description = "API 消费者 UUID。") @PathVariable UUID id) {
        return service.sync(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "立即对账 API 消费者网关状态")
    @PostMapping("/{id}/actions/reconcile-gateway")
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "立即对账 API 消费者网关状态", description = "逐个检查所有已记录网关绑定与当前消费者定义是否一致，并更新存在性、漂移和错误状态；该操作只检查和记录，不创建或覆盖远端消费者。单个网关检查失败仍返回 200，并在对应绑定记录 CHECK_FAILED。")
    public ApiConsumerResponse reconcileGateway(@Parameter(description = "API 消费者 UUID。") @PathVariable UUID id) {
        return service.reconcileGateway(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "从网关和 DataScalpel 删除 API 消费者")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.update')")
    @Operation(summary = "从网关和 DataScalpel 删除 API 消费者", description = "先从所有网关删除消费者绑定，再删除本地消费者。仍有服务订阅或 API Key 时拒绝；任一远端删除失败时保留失败状态且不伪装为成功。")
    public void delete(@Parameter(description = "API 消费者 UUID。") @PathVariable UUID id) {
        service.delete(id);
    }
}

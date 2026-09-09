package cn.superhuang.data.scalpel.business.service.consumer.credential.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.consumer.credential.service.ApiConsumerCredentialService;
import cn.superhuang.data.scalpel.business.service.consumer.credential.web.request.CreateApiConsumerCredentialRequest;
import cn.superhuang.data.scalpel.business.service.consumer.credential.web.response.ApiConsumerCredentialResponse;
import cn.superhuang.data.scalpel.business.service.consumer.credential.web.response.ApiConsumerCredentialSecretResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/api-consumers/{consumerId}/credentials")
@Tag(name = "数据服务-API 消费者凭证")
public class ApiConsumerCredentialResource {

    private final ApiConsumerCredentialService service;

    public ApiConsumerCredentialResource(ApiConsumerCredentialService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 API Consumer 的调用凭证")
    @GetMapping
    @PreAuthorize("hasAuthority('service.view')")
    @Operation(summary = "查询 API Consumer 的调用凭证")
    public List<ApiConsumerCredentialResponse> list(@PathVariable UUID consumerId) {
        return service.list(consumerId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "创建 API Key，明文只在本次成功响应中返回")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "创建 API Key，明文只在本次成功响应中返回")
    public ApiConsumerCredentialSecretResponse create(
            @PathVariable UUID consumerId,
            @Valid @RequestBody CreateApiConsumerCredentialRequest request
    ) {
        return service.create(consumerId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "轮换 API Key，明文只在本次成功响应中返回")
    @PostMapping("/{credentialId}/actions/rotate")
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "轮换 API Key，明文只在本次成功响应中返回")
    public ApiConsumerCredentialSecretResponse rotate(
            @PathVariable UUID consumerId,
            @PathVariable UUID credentialId
    ) {
        return service.rotate(consumerId, credentialId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "立即对账 API Key 网关状态")
    @PostMapping("/{credentialId}/actions/reconcile-gateway")
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "立即对账 API Key 网关状态")
    public ApiConsumerCredentialResponse reconcileGateway(
            @PathVariable UUID consumerId,
            @PathVariable UUID credentialId
    ) {
        return service.reconcileGateway(consumerId, credentialId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "从网关和 DataScalpel 删除 API Key")
    @PostMapping("/{credentialId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "从网关和 DataScalpel 删除 API Key")
    public void delete(
            @PathVariable UUID consumerId,
            @PathVariable UUID credentialId
    ) {
        service.delete(consumerId, credentialId);
    }
}

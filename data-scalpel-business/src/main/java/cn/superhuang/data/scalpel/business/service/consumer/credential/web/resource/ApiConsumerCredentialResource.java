package cn.superhuang.data.scalpel.business.service.consumer.credential.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.consumer.credential.service.ApiConsumerCredentialService;
import cn.superhuang.data.scalpel.business.service.consumer.credential.web.request.CreateApiConsumerCredentialRequest;
import cn.superhuang.data.scalpel.business.service.consumer.credential.web.response.ApiConsumerCredentialResponse;
import cn.superhuang.data.scalpel.business.service.consumer.credential.web.response.ApiConsumerCredentialSecretResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    @Operation(summary = "查询 API Consumer 的调用凭证", description = "按创建时间倒序返回消费者的 API Key 名称、提示、轮换版本及各网关同步状态；永远不返回完整密钥或数据库摘要。")
    public List<ApiConsumerCredentialResponse> list(@Parameter(description = "API 调用方 UUID。") @PathVariable UUID consumerId) {
        return service.list(consumerId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "创建 API Key，明文只在本次成功响应中返回")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "创建 API Key，明文只在本次成功响应中返回", description = "要求消费者当前 revision 已同步到活动网关，然后生成随机 API Key，数据库只保存摘要并同步到该网关。每个消费者最多 10 个。网关同步成功时返回 201 且 secret 只显示一次；远端失败仍返回 201、绑定为 SYNC_FAILED 且 secret=null，本次明文无法恢复，只能删除凭证或再次轮换。")
    public ApiConsumerCredentialSecretResponse create(
            @Parameter(description = "API 调用方 UUID。") @PathVariable UUID consumerId,
            @Valid @RequestBody CreateApiConsumerCredentialRequest request
    ) {
        return service.create(consumerId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "轮换 API Key，明文只在本次成功响应中返回")
    @PostMapping("/{credentialId}/actions/rotate")
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "轮换 API Key，明文只在本次成功响应中返回", description = "先生成新密钥并替换本地摘要、递增 revision，再同步到活动网关。同步成功时新 secret 只显示一次，旧网关密钥随远端替换失效；远端失败仍返回 200、绑定为 SYNC_FAILED 且 secret=null，本次新明文无法恢复，旧网关密钥是否仍有效取决于远端执行结果，应对账后再次轮换。30 秒内已有凭证操作时拒绝执行。")
    public ApiConsumerCredentialSecretResponse rotate(
            @Parameter(description = "API 调用方 UUID。") @PathVariable UUID consumerId,
            @Parameter(description = "API 调用凭证 UUID。") @PathVariable UUID credentialId
    ) {
        return service.rotate(consumerId, credentialId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "立即对账 API Key 网关状态")
    @PostMapping("/{credentialId}/actions/reconcile-gateway")
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "立即对账 API Key 网关状态", description = "检查该凭证在所有已知网关中的存在性、归属和当前秘密摘要一致性，更新漂移与错误状态；不会返回密钥，也不会自动覆盖远端凭证。单个网关检查失败仍返回 200，并在对应绑定记录 CHECK_FAILED。")
    public ApiConsumerCredentialResponse reconcileGateway(
            @Parameter(description = "API 调用方 UUID。") @PathVariable UUID consumerId,
            @Parameter(description = "API 调用凭证 UUID。") @PathVariable UUID credentialId
    ) {
        return service.reconcileGateway(consumerId, credentialId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "从网关和 DataScalpel 删除 API Key")
    @PostMapping("/{credentialId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.publish')")
    @Operation(summary = "从网关和 DataScalpel 删除 API Key", description = "先从所有网关删除凭证，再删除本地摘要。远端删除失败时保留凭证和失败绑定状态，后续可重试。")
    public void delete(
            @Parameter(description = "API 调用方 UUID。") @PathVariable UUID consumerId,
            @Parameter(description = "API 调用凭证 UUID。") @PathVariable UUID credentialId
    ) {
        service.delete(consumerId, credentialId);
    }
}

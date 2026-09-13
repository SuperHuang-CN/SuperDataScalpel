package cn.superhuang.data.scalpel.business.datasource.web.resource;

import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.datasource.service.ApiResourceService;
import cn.superhuang.data.scalpel.business.datasource.service.ApiResourceRuntimeService;
import cn.superhuang.data.scalpel.business.datasource.web.request.CreateApiResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.TestApiResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.UpdateApiResourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.ApiResourceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.ApiResourceTestResponse;
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
@RequestMapping("/api/v1/data-sources/{dataSourceId}/api-resources")
@Tag(name = "HTTP API 资源")
public class ApiResourceResource {

    private final ApiResourceService service;
    private final ApiResourceRuntimeService runtimeService;

    public ApiResourceResource(ApiResourceService service, ApiResourceRuntimeService runtimeService) {
        this.service = service;
        this.runtimeService = runtimeService;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 HTTP API 资源")
    @GetMapping
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询 HTTP API 资源", description = "读取指定 HTTP API 数据源下已登记的全部资源定义，按资源名称升序返回；不分页，也不向远端 API 发起请求。数据源停用时仍可查看。")
    public List<ApiResourceResponse> list(
            @Parameter(description = "HTTP API 数据源 UUID") @PathVariable UUID dataSourceId
    ) {
        return service.list(dataSourceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询 HTTP API 资源详情")
    @GetMapping("/{resourceId}")
    @PreAuthorize("hasAuthority('datasource.view')")
    @Operation(summary = "查询 HTTP API 资源详情", description = "读取一个已登记 HTTP API 资源的请求模板、参数 Schema 和响应提取配置，不调用远端接口。")
    public ApiResourceResponse get(
            @Parameter(description = "HTTP API 数据源 UUID") @PathVariable UUID dataSourceId,
            @Parameter(description = "HTTP API 资源 UUID") @PathVariable UUID resourceId
    ) {
        return service.get(dataSourceId, resourceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "新增 HTTP API 资源")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('datasource.create')")
    @Operation(summary = "新增 HTTP API 资源", description = "在 HTTP_API 类型数据源下校验并保存资源定义；当前 connectorType 只支持 GENERIC_HTTP。不会隐式调用远端接口或验证真实响应，数据源停用时也可维护资源。")
    public ApiResourceResponse create(
            @Parameter(description = "HTTP API 数据源 UUID") @PathVariable UUID dataSourceId,
            @Valid @RequestBody CreateApiResourceRequest request
    ) {
        return service.create(dataSourceId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "修改 HTTP API 资源")
    @PostMapping("/{resourceId}/actions/update")
    @PreAuthorize("hasAuthority('datasource.update')")
    @Operation(summary = "修改 HTTP API 资源", description = "整体替换已登记 HTTP API 资源的名称、启停状态、请求、签名、分页或异步轮询、输出字段和执行上限；资源编码与所属数据源保持不变，不会隐式发起远端请求。")
    public ApiResourceResponse update(
            @Parameter(description = "HTTP API 数据源 UUID") @PathVariable UUID dataSourceId,
            @Parameter(description = "HTTP API 资源 UUID") @PathVariable UUID resourceId,
            @Valid @RequestBody UpdateApiResourceRequest request
    ) {
        return service.update(dataSourceId, resourceId, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "删除 HTTP API 资源")
    @PostMapping("/{resourceId}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('datasource.delete')")
    @Operation(summary = "删除 HTTP API 资源", description = "立即删除管理库中的 HTTP API 资源定义，不影响远端接口。当前不会预检已有任务定义中的资源 UUID 引用；删除后这些任务在重新校验或运行时会因资源不存在而失败。")
    public void delete(
            @Parameter(description = "HTTP API 数据源 UUID") @PathVariable UUID dataSourceId,
            @Parameter(description = "HTTP API 资源 UUID") @PathVariable UUID resourceId
    ) {
        service.delete(dataSourceId, resourceId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "测试 HTTP API 资源")
    @PostMapping("/{resourceId}/actions/test")
    @PreAuthorize("hasAuthority('datasource.test')")
    @Operation(summary = "测试 HTTP API 资源", description = "使用已保存且启用的数据源和资源真实执行远端调用，不修改或保存管理库配置。测试最多执行 3 页、解析 10000 条、累计读取 10 MiB、持续 60 秒，并只返回前 20 条样例；更小的资源自定义上限仍生效。连接配置的网络和可重试 HTTP 错误重试策略仍生效。ASYNC_JOB 会真实提交、轮询并读取远端任务，超时或中断后不能据此确认远端任务未创建。")
    public ApiResourceTestResponse test(
            @Parameter(description = "HTTP API 数据源 UUID") @PathVariable UUID dataSourceId,
            @Parameter(description = "HTTP API 资源 UUID") @PathVariable UUID resourceId,
            @Valid @RequestBody(required = false) TestApiResourceRequest request
    ) {
        return runtimeService.test(dataSourceId, resourceId, request);
    }
}

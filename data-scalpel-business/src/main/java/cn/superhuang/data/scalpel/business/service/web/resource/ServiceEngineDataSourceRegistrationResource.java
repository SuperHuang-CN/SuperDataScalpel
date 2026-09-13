package cn.superhuang.data.scalpel.business.service.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.ServiceEngineDataSourceRegistrationService;
import cn.superhuang.data.scalpel.business.service.web.request.CreateServiceEngineDataSourceRegistrationRequest;
import cn.superhuang.data.scalpel.business.service.web.request.ServiceEngineDataSourceActionRequest;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineDataSourceRegistrationResponse;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineDataSourceTestResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
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

/** Admin management API for Engine-local data source registrations. */
@io.swagger.v3.oas.annotations.tags.Tag(name = "服务引擎数据源登记")
@RestController
@RequestMapping("/api/v1/service-engine-data-sources")
public class ServiceEngineDataSourceRegistrationResource {

    private final ServiceEngineDataSourceRegistrationService service;

    public ServiceEngineDataSourceRegistrationResource(ServiceEngineDataSourceRegistrationService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "服务引擎数据源登记：查询列表")
    @Operation(summary = "查询服务引擎数据源登记", description = "按通用 Search DSL 分页查询管理端记录的引擎与数据源绑定、同步状态和最近错误；不会实时访问远端引擎。")
    @GetMapping
    @PreAuthorize("hasAuthority('service.engine.view')")
    public PageResponse<ServiceEngineDataSourceRegistrationResponse> search(
            @ParameterObject @ModelAttribute SearchRequest request
    ) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "服务引擎数据源登记：查看详情")
    @Operation(summary = "查看服务引擎数据源登记", description = "读取一个引擎数据源登记的目标引擎、平台数据源、同步状态、快照摘要和最近错误，不返回连接密码。")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.engine.view')")
    public ServiceEngineDataSourceRegistrationResponse get(@Parameter(description = "服务引擎数据源注册 UUID。") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "服务引擎数据源登记：创建")
    @Operation(summary = "创建服务引擎数据源登记", description = "先保存唯一绑定，再在事务外同步当前 JDBC 连接快照。DataScalpel 要求已启用且方言支持 SQL 服务查询；GeoServer 还要求 PostgreSQL、STORAGE 用途并在目标库检测到 PostGIS。远端失败不会回滚登记，接口仍返回 201，使用 status=FAILED 和 lastError 表示从未同步成功。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.engine.update')")
    public ServiceEngineDataSourceRegistrationResponse create(
            @Valid @RequestBody CreateServiceEngineDataSourceRegistrationRequest request
    ) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "服务引擎数据源登记：同步")
    @Operation(summary = "同步服务引擎数据源", description = "要求引擎启用且数据源仍满足运行条件，重新读取当前连接配置并同步覆盖到目标引擎。远端失败不会作为 HTTP 错误抛出：接口返回 200，首次失败为 FAILED，曾成功过则为 OUTDATED，并在 lastError 中给出安全摘要。")
    @PostMapping("/{id}/actions/sync")
    @PreAuthorize("hasAuthority('service.engine.update')")
    public ServiceEngineDataSourceRegistrationResponse sync(
            @Parameter(description = "服务引擎数据源注册 UUID。") @PathVariable UUID id,
            @Valid @RequestBody ServiceEngineDataSourceActionRequest request
    ) {
        return service.sync(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "服务引擎数据源登记：测试连接")
    @Operation(summary = "测试引擎数据源连接", description = "要求登记状态为 READY；即使引擎已在 Admin 停用也允许测试。DataScalpel Engine 使用其本地连接池，GeoServer 测试 DataStore。成功不修改登记状态，远端失败返回 502。")
    @PostMapping("/{id}/actions/test")
    @PreAuthorize("hasAuthority('service.engine.test')")
    public ServiceEngineDataSourceTestResponse test(
            @Parameter(description = "服务引擎数据源注册 UUID。") @PathVariable UUID id,
            @Valid @RequestBody ServiceEngineDataSourceActionRequest request
    ) {
        return service.test(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "服务引擎数据源登记：删除")
    @Operation(summary = "删除服务引擎数据源登记", description = "先确认同一引擎上没有已启用数据服务使用该数据源，再从远端移除，最后重新校验并删除管理端登记；草稿或停用服务不会阻止。引擎即使已停用仍会尝试远端移除；远端失败返回 502 并保留登记。")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.engine.update')")
    public void delete(
            @Parameter(description = "服务引擎数据源注册 UUID。") @PathVariable UUID id,
            @Valid @RequestBody ServiceEngineDataSourceActionRequest request
    ) {
        service.delete(id);
    }
}

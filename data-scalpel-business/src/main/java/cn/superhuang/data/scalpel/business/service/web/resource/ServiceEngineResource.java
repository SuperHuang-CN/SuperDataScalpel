package cn.superhuang.data.scalpel.business.service.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.ServiceEngineManagementService;
import cn.superhuang.data.scalpel.business.service.web.request.CreateServiceEngineRequest;
import cn.superhuang.data.scalpel.business.service.web.request.TestServiceEngineRequest;
import cn.superhuang.data.scalpel.business.service.web.request.TestStoredServiceEngineRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateServiceEngineRequest;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineResponse;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineTestResponse;
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

@io.swagger.v3.oas.annotations.tags.Tag(name = "服务引擎")
@RestController
@RequestMapping("/api/v1/service-engines")
public class ServiceEngineResource {

    private final ServiceEngineManagementService service;

    public ServiceEngineResource(ServiceEngineManagementService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "服务引擎：查询列表")
    @Operation(summary = "查询服务引擎", description = "按通用 Search DSL 分页查询 DataScalpel Service Engine 与 GeoServer 引擎登记；返回配置和登记状态，不探测远端运行状态。")
    @GetMapping
    @PreAuthorize("hasAuthority('service.engine.view')")
    public PageResponse<ServiceEngineResponse> search(@ParameterObject @ModelAttribute SearchRequest request) {
        return service.search(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "服务引擎：查看详情")
    @Operation(summary = "查看服务引擎详情", description = "读取一个已登记服务引擎的管理地址、运行地址、类型、启用状态和最近配置；不会返回管理令牌或 GeoServer 密码。")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('service.engine.view')")
    public ServiceEngineResponse get(@Parameter(description = "服务引擎 UUID。") @PathVariable UUID id) {
        return service.get(id);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "服务引擎：创建")
    @Operation(summary = "创建服务引擎", description = "先使用提交凭据同步连接远端管理端，成功后才保存登记。DataScalpel 只探测 adminUrl 的 /info，编码取自远端，runtimeUrl 仅做格式校验；GeoServer 使用请求编码并发现版本、工作区及管理能力。enabled=false 仍会先测试，只阻止后续 Admin 管理动作。")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('service.engine.create')")
    public ServiceEngineResponse create(@Valid @RequestBody CreateServiceEngineRequest request) {
        return service.create(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "服务引擎：修改")
    @Operation(summary = "修改服务引擎", description = "整体修改名称、地址、凭据、工作区、启用状态和说明，类型与编码不变。DataScalpel 仅在 adminUrl、runtimeUrl 或 Token 变化时探测管理端，并在变化后把访问策略标为 OUTDATED；GeoServer 每次更新都重新发现远端。已有数据源登记或数据服务的 GeoServer 不能改变 adminUrl 或工作区。停用不会停止远端已有部署。")
    @PostMapping("/{id}/actions/update")
    @PreAuthorize("hasAuthority('service.engine.update')")
    public ServiceEngineResponse update(@Parameter(description = "服务引擎 UUID。") @PathVariable UUID id, @Valid @RequestBody UpdateServiceEngineRequest request) {
        return service.update(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "服务引擎：测试已保存配置")
    @Operation(summary = "测试已保存的服务引擎", description = "使用已保存配置测试远端身份和能力；请求体可省略，适用于当前引擎类型的非空字段可临时覆盖且不写回。DataScalpel 只使用 adminUrl 和 managementToken，忽略 runtimeUrl；GeoServer 使用管理地址、运行地址、用户名、密码和工作区。")
    @PostMapping("/{id}/actions/test")
    @PreAuthorize("hasAuthority('service.engine.test')")
    public ServiceEngineTestResponse test(
            @Parameter(description = "服务引擎 UUID。") @PathVariable UUID id,
            @Valid @RequestBody(required = false) TestStoredServiceEngineRequest request
    ) {
        return service.test(id, request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.EXECUTE, summary = "服务引擎：测试新配置")
    @Operation(summary = "测试新的服务引擎配置", description = "在创建前使用请求参数同步探测远端并返回身份、能力和耗时，不保存登记。DataScalpel 只调用 adminUrl 的 /info，忽略 code、runtimeUrl 和 GeoServer 字段；GeoServer 使用其全部类型专属连接参数。")
    @PostMapping("/actions/test")
    @PreAuthorize("hasAuthority('service.engine.test')")
    public ServiceEngineTestResponse test(@Valid @RequestBody TestServiceEngineRequest request) {
        return service.test(request);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.WRITE, summary = "服务引擎：删除")
    @Operation(summary = "删除服务引擎", description = "删除未被数据源登记或数据服务引用的引擎，并清除其访问策略；存在引用时返回冲突且不删除。")
    @PostMapping("/{id}/actions/delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('service.engine.delete')")
    public void delete(@Parameter(description = "服务引擎 UUID。") @PathVariable UUID id) {
        service.delete(id);
    }
}

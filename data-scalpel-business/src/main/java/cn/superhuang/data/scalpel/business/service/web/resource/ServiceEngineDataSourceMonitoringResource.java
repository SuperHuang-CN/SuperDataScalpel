package cn.superhuang.data.scalpel.business.service.web.resource;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import cn.superhuang.data.scalpel.business.systemmcp.metadata.SystemMcpOperation;
import cn.superhuang.data.scalpel.business.service.ServiceEngineDataSourceMonitoringService;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourcePoolMonitorResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourcePoolSummariesResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Read-only JDBC monitoring, authenticated through Admin; no Engine token reaches the browser. */
@Tag(name = "服务引擎数据源监控")
@RestController
@RequestMapping("/api/v1")
public class ServiceEngineDataSourceMonitoringResource {
    private final ServiceEngineDataSourceMonitoringService service;

    public ServiceEngineDataSourceMonitoringResource(ServiceEngineDataSourceMonitoringService service) {
        this.service = service;
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查询服务引擎数据源连接池概览")
    @Operation(summary = "查询服务引擎数据源连接池概览", description = "允许读取已停用的 DataScalpel Engine。没有任何 Admin 登记时直接返回本地空列表，不访问远端；否则读取实时连接池摘要，只保留 Admin 已登记的数据源，远端缺少的登记补为 NOT_LOADED，远端额外数据源被过滤。查询不修改登记或引擎状态。")
    @GetMapping("/service-engines/{engineId}/data-source-pools")
    @PreAuthorize("hasAuthority('service.engine.view')")
    public EngineDataSourcePoolSummariesResponse summaries(@Parameter(description = "DataScalpel Service Engine UUID；GeoServer 不支持连接池监控。") @PathVariable UUID engineId) {
        return service.summaries(engineId);
    }

    @SystemMcpOperation(value = SystemMcpOperation.Effect.READ, summary = "查看服务引擎数据源连接池详情")
    @Operation(summary = "查看服务引擎数据源连接池详情", description = "根据任意状态的管理端登记读取 DataScalpel Engine 实时连接池状态、活跃连接、主要调用方、近期 SQL 和异常事件；不要求登记 READY 或引擎启用。远端不可达、超时、无此监控接口或身份不匹配统一返回 502，且不改变登记或引擎状态。")
    @GetMapping("/service-engine-data-sources/{id}/pool-monitor")
    @PreAuthorize("hasAuthority('service.engine.view')")
    public EngineDataSourcePoolMonitorResponse detail(@Parameter(description = "服务引擎数据源注册 UUID。") @PathVariable UUID id) {
        return service.detail(id);
    }
}

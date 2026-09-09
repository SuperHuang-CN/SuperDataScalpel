package cn.superhuang.data.scalpel.business.service.web.resource;

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
@RestController
@RequestMapping("/api/v1")
public class ServiceEngineDataSourceMonitoringResource {
    private final ServiceEngineDataSourceMonitoringService service;

    public ServiceEngineDataSourceMonitoringResource(ServiceEngineDataSourceMonitoringService service) {
        this.service = service;
    }

    @GetMapping("/service-engines/{engineId}/data-source-pools")
    @PreAuthorize("hasAuthority('service.engine.view')")
    public EngineDataSourcePoolSummariesResponse summaries(@PathVariable UUID engineId) {
        return service.summaries(engineId);
    }

    @GetMapping("/service-engine-data-sources/{id}/pool-monitor")
    @PreAuthorize("hasAuthority('service.engine.view')")
    public EngineDataSourcePoolMonitorResponse detail(@PathVariable UUID id) {
        return service.detail(id);
    }
}

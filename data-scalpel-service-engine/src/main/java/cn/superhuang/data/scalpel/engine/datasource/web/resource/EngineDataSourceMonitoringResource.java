package cn.superhuang.data.scalpel.engine.datasource.web.resource;

import cn.superhuang.data.scalpel.contract.service.EngineDataSourcePoolMonitorResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourcePoolSummariesResponse;
import cn.superhuang.data.scalpel.engine.datasource.EngineDataSourceMonitoringService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Read-only diagnostics protected by the existing Engine Management Token filter. */
@RestController
@RequestMapping("/internal/v1/data-sources")
public class EngineDataSourceMonitoringResource {
    private final EngineDataSourceMonitoringService service;

    public EngineDataSourceMonitoringResource(EngineDataSourceMonitoringService service) {
        this.service = service;
    }

    @GetMapping("/pool-summaries")
    public EngineDataSourcePoolSummariesResponse summaries() {
        return service.summaries();
    }

    @GetMapping("/{dataSourceId}/pool-monitor")
    public EngineDataSourcePoolMonitorResponse detail(@PathVariable UUID dataSourceId) {
        return service.detail(dataSourceId);
    }
}

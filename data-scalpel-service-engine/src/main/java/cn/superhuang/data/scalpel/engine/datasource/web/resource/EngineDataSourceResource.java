package cn.superhuang.data.scalpel.engine.datasource.web.resource;

import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRemovalRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceTestResponse;
import cn.superhuang.data.scalpel.engine.datasource.EngineDataSourceStore;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Token-protected Engine control endpoints for local data sources. */
@RestController
@RequestMapping("/internal/v1/data-sources")
public class EngineDataSourceResource {

    private final EngineDataSourceStore store;

    public EngineDataSourceResource(EngineDataSourceStore store) {
        this.store = store;
    }

    @PostMapping
    public EngineDataSourceRegistrationResponse register(@Valid @RequestBody EngineDataSourceRegistrationRequest request) {
        return store.register(request);
    }

    @PostMapping("/{dataSourceId}/actions/test")
    public EngineDataSourceTestResponse test(@PathVariable UUID dataSourceId) {
        return store.test(dataSourceId);
    }

    @PostMapping("/actions/remove")
    public EngineDataSourceRegistrationResponse remove(@Valid @RequestBody EngineDataSourceRemovalRequest request) {
        return store.remove(request);
    }
}

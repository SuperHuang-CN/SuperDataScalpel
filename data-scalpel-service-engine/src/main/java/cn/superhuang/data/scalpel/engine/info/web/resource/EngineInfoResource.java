package cn.superhuang.data.scalpel.engine.info.web.resource;

import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.contract.service.ServiceEngineInfoResponse;
import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1")
public class EngineInfoResource {

    private final EngineProperties properties;
    private final DialectRegistry dialectRegistry;

    public EngineInfoResource(EngineProperties properties, DialectRegistry dialectRegistry) {
        this.properties = properties;
        this.dialectRegistry = dialectRegistry;
    }

    @GetMapping("/info")
    public ServiceEngineInfoResponse info() {
        return new ServiceEngineInfoResponse(
                properties.code(),
                dialectRegistry.all().stream().map(dialect -> dialect.definition().id()).toList()
        );
    }
}

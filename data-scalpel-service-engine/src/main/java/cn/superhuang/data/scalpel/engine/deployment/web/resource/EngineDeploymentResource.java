package cn.superhuang.data.scalpel.engine.deployment.web.resource;

import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceUndeploymentRequest;
import cn.superhuang.data.scalpel.engine.deployment.EngineRuntimeDeploymentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/deployments")
public class EngineDeploymentResource {

    private final EngineRuntimeDeploymentService service;

    public EngineDeploymentResource(EngineRuntimeDeploymentService service) {
        this.service = service;
    }

    @PostMapping
    public ServiceDeploymentResponse deploy(@Valid @RequestBody ServiceDeploymentRequest request) {
        return service.deploy(request);
    }

    @PostMapping("/actions/remove")
    public ServiceDeploymentResponse remove(@Valid @RequestBody ServiceUndeploymentRequest request) {
        return service.remove(request);
    }
}

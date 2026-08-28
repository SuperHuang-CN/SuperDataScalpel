package cn.superhuang.data.scalpel.engine.accesspolicy.web.resource;

import cn.superhuang.data.scalpel.contract.service.EngineAccessPolicyApplyRequest;
import cn.superhuang.data.scalpel.contract.service.EngineAccessPolicyApplyResponse;
import cn.superhuang.data.scalpel.engine.accesspolicy.EngineAccessPolicyService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/access-policy")
public class EngineAccessPolicyResource {

    private final EngineAccessPolicyService service;

    public EngineAccessPolicyResource(EngineAccessPolicyService service) {
        this.service = service;
    }

    @PostMapping("/actions/apply")
    public EngineAccessPolicyApplyResponse apply(@Valid @RequestBody EngineAccessPolicyApplyRequest request) {
        return service.apply(request);
    }
}

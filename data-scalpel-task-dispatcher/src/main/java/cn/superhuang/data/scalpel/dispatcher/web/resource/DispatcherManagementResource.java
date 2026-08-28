package cn.superhuang.data.scalpel.dispatcher.web.resource;

import cn.superhuang.data.scalpel.dispatcher.management.DispatcherDeactivateRequest;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherInfoResponse;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherRegistrationRequest;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherRegistrationResponse;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherRegistrationService;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherRuntimeService;
import cn.superhuang.data.scalpel.contract.execution.DispatcherRuntimeOverviewResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dispatcher")
public class DispatcherManagementResource {
    private final DispatcherRegistrationService service;
    private final DispatcherRuntimeService runtimeService;

    public DispatcherManagementResource(DispatcherRegistrationService service, DispatcherRuntimeService runtimeService) {
        this.service = service;
        this.runtimeService = runtimeService;
    }

    @GetMapping("/info")
    public DispatcherInfoResponse info() { return service.info(); }

    @GetMapping("/runtime-overview")
    public DispatcherRuntimeOverviewResponse runtimeOverview() { return runtimeService.overview(); }

    @GetMapping("/registration")
    public DispatcherRegistrationResponse registration() { return service.current(); }

    @PostMapping("/registration/actions/activate")
    public DispatcherRegistrationResponse activate(@Valid @RequestBody DispatcherRegistrationRequest request) {
        return service.activate(request);
    }

    @PostMapping("/registration/actions/drain")
    public DispatcherRegistrationResponse drain() { return service.drain(); }

    @PostMapping("/registration/actions/deactivate")
    public DispatcherRegistrationResponse deactivate(@RequestBody(required = false) DispatcherDeactivateRequest request) {
        return service.deactivate(request != null && request.force());
    }
}

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
import org.springframework.web.bind.annotation.RequestParam;
import java.util.UUID;

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
    public DispatcherInfoResponse info(@RequestParam(required = false) UUID engineId,
                                       @RequestParam(required = false) String targetKey) {
        if (engineId != null && targetKey != null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "engineId 与 targetKey 不能同时指定");
        return targetKey == null ? service.info(engineId) : service.targetInfo(targetKey);
    }

    @GetMapping("/targets")
    public cn.superhuang.data.scalpel.contract.execution.DispatcherTargetDirectoryResponse targets() { return service.targets(); }

    @GetMapping("/runtime-overview")
    public DispatcherRuntimeOverviewResponse runtimeOverview(@RequestParam(required = false) UUID engineId) { return runtimeService.overview(engineId); }

    @GetMapping("/registration")
    public DispatcherRegistrationResponse registration(@RequestParam(required = false) UUID engineId) { return service.current(engineId); }

    @PostMapping("/registration/actions/activate")
    public DispatcherRegistrationResponse activate(@Valid @RequestBody DispatcherRegistrationRequest request) {
        return service.activate(request);
    }

    @PostMapping("/registration/actions/drain")
    public DispatcherRegistrationResponse drain(@RequestParam(required = false) UUID engineId) { return service.drain(engineId); }

    @PostMapping("/registration/actions/resume")
    public DispatcherRegistrationResponse resume(@RequestParam(required = false) UUID engineId) { return service.resume(engineId); }

    @PostMapping("/registration/actions/deactivate")
    public DispatcherRegistrationResponse deactivate(@RequestParam(required = false) UUID engineId, @RequestBody(required = false) DispatcherDeactivateRequest request) {
        return service.deactivate(engineId, request != null && request.force());
    }
}

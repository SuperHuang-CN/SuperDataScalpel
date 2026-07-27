package cn.superhuang.data.scalpel.dispatcher.web.resource;

import cn.superhuang.data.scalpel.dispatcher.backend.TaskExecutionBackend;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherListenerManager;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherIdentityRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherArtifactService;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendReadiness;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherTopics;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
public class DispatcherHealthResource {
    private final DispatcherIdentityRepository identityRepository;
    private final TaskExecutionBackend backend;
    private final DispatcherListenerManager listenerManager;
    private final DispatcherRegistrationRepository registrationRepository;
    private final DispatcherArtifactService artifactService;

    public DispatcherHealthResource(
            DispatcherIdentityRepository identityRepository,
            TaskExecutionBackend backend,
            DispatcherListenerManager listenerManager,
            DispatcherRegistrationRepository registrationRepository,
            DispatcherArtifactService artifactService
    ) {
        this.identityRepository = identityRepository;
        this.backend = backend;
        this.listenerManager = listenerManager;
        this.registrationRepository = registrationRepository;
        this.artifactService = artifactService;
    }

    @GetMapping("/live")
    public Map<String, String> live() { return Map.of("status", "UP"); }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        boolean database = identityRepository.count() >= 0;
        boolean backendReady = backend.readiness().ready();
        var registration = registrationRepository.findFirstByOrderByCreatedAtAsc();
        boolean activeRegistration = registration
                .map(value -> value.getState() == DispatcherRegistrationState.ACTIVE
                        || value.getState() == DispatcherRegistrationState.DRAINING)
                .orElse(false);
        boolean listenerReady = !activeRegistration || listenerManager.listenersRunning();
        BackendReadiness messaging = listenerManager.readiness(registration
                .map(value -> new DispatcherTopics(
                        value.getCommandTopic(), value.getRunnerEventTopic(), value.getAdminEventTopic(),
                        value.getRunnerControlTopic()))
                .orElse(null));
        boolean artifactReady = artifactService.readiness().ready();
        boolean ready = database && backendReady && artifactReady && messaging.ready() && listenerReady;
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "status", ready ? "UP" : "DOWN",
                "database", database ? "UP" : "DOWN",
                "backend", backendReady ? "UP" : "DOWN",
                "artifacts", artifactReady ? "UP" : "DOWN",
                "kafka", messaging.ready() ? "UP" : "DOWN",
                "listeners", !activeRegistration ? "INACTIVE" : listenerReady ? "UP" : "DOWN"
        ));
    }
}

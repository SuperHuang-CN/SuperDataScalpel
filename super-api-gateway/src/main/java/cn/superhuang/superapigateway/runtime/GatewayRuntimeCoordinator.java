package cn.superhuang.superapigateway.runtime;

import cn.superhuang.superapigateway.controlplane.service.ConfigurationChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.net.InetAddress;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class GatewayRuntimeCoordinator {

    private static final Logger log = LoggerFactory.getLogger(GatewayRuntimeCoordinator.class);

    private final GatewayRuntimeSnapshotLoader loader;
    private final GatewayRuntimeHolder holder;
    private final RuntimePersistenceService persistence;
    private final ExecutorService executor;
    private final AtomicBoolean loading = new AtomicBoolean();
    private final AtomicBoolean reloadRequested = new AtomicBoolean();
    private final String instanceId;
    private final String applicationVersion;
    private final Instant startedAt = Instant.now();

    public GatewayRuntimeCoordinator(
            GatewayRuntimeSnapshotLoader loader,
            GatewayRuntimeHolder holder,
            RuntimePersistenceService persistence,
            @Qualifier("snapshotReloadExecutor") ExecutorService executor,
            @Value("${SUPER_API_GATEWAY_INSTANCE_ID:}") String configuredInstanceId,
            @Value("${info.application.version:unknown}") String applicationVersion
    ) {
        this.loader = loader;
        this.holder = holder;
        this.persistence = persistence;
        this.executor = executor;
        this.instanceId = configuredInstanceId == null || configuredInstanceId.isBlank()
                ? defaultInstanceId() : configuredInstanceId.trim();
        this.applicationVersion = applicationVersion;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ready() {
        requestReload();
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void configurationChanged(ConfigurationChangedEvent event) {
        requestReload();
    }

    public void requestReload() {
        reloadRequested.set(true);
        if (loading.compareAndSet(false, true)) {
            executor.execute(this::reloadLoop);
        }
    }

    @Scheduled(fixedDelayString = "${super-api-gateway.runtime.revision-poll-interval:5s}")
    public void pollRevision() {
        executor.execute(() -> {
            try {
                if (persistence.currentRevision() > holder.snapshot().revision()) requestReload();
            } catch (RuntimeException exception) {
                log.debug("Unable to poll gateway configuration revision", exception);
            }
        });
    }

    @Scheduled(fixedDelayString = "${super-api-gateway.runtime.heartbeat-interval:10s}")
    public void heartbeat() {
        executor.execute(() -> {
            try {
                var status = holder.status();
                persistence.heartbeat(
                        instanceId,
                        startedAt,
                        status.state(),
                        holder.snapshot().revision(),
                        status.lastError(),
                        applicationVersion
                );
            } catch (RuntimeException exception) {
                log.debug("Unable to persist gateway heartbeat", exception);
            }
        });
    }

    public String instanceId() {
        return instanceId;
    }

    private void reloadLoop() {
        try {
            do {
                reloadRequested.set(false);
                try {
                    GatewayRuntimeSnapshot snapshot = loader.load();
                    holder.install(snapshot);
                    log.info("Installed gateway runtime snapshot revision {}", snapshot.revision());
                } catch (RuntimeException exception) {
                    holder.failed(exception);
                    log.error("Gateway runtime snapshot reload failed; keeping revision {}",
                            holder.snapshot().revision(), exception);
                }
            } while (reloadRequested.get());
        } finally {
            loading.set(false);
            if (reloadRequested.get()) requestReload();
        }
    }

    private static String defaultInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception exception) {
            return "gateway-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}

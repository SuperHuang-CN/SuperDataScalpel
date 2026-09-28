package cn.superhuang.data.scalpel.dispatcher.service;

import cn.superhuang.data.scalpel.dispatcher.backend.DispatcherBackendRegistry;
import cn.superhuang.data.scalpel.dispatcher.config.DispatcherTargetsProperties;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistration;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherRegistrationState;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherRegistrationRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Persistent queues stay in the database; worker pools have no in-memory task queue. */
@Component
public class DispatcherEngineScheduler {
    private static final Logger log = LoggerFactory.getLogger(DispatcherEngineScheduler.class);
    private final DispatcherRegistrationRepository registrations;
    private final DispatcherExecutionCoordinator coordinator;
    private final DispatcherBackendRegistry backends;
    private final cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository executions;
    private final ThreadPoolExecutor submitters;
    private final ThreadPoolExecutor observers;
    private final ThreadPoolExecutor cleaners;
    private final ConcurrentHashMap<String, AtomicInteger> active = new ConcurrentHashMap<>();
    private final AtomicInteger cursor = new AtomicInteger();

    public DispatcherEngineScheduler(DispatcherRegistrationRepository registrations, DispatcherExecutionCoordinator coordinator,
            DispatcherBackendRegistry backends, DispatcherTargetsProperties limits,
            cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository executions) {
        this.registrations = registrations;
        this.coordinator = coordinator;
        this.backends = backends;
        this.executions = executions;
        submitters = pool(limits.maxConcurrentSubmissions(), "dispatcher-submit-");
        observers = pool(limits.maintenanceConcurrency() / 2, "dispatcher-observe-");
        cleaners = pool(limits.maintenanceConcurrency() - limits.maintenanceConcurrency() / 2, "dispatcher-cleanup-");
    }

    @Scheduled(scheduler = "dispatcherAdmissionScheduler", fixedDelayString = "${data-scalpel.dispatcher.admission-poll-interval:500ms}")
    public void admit() {
        for (var registration : rotated()) {
            if (registration.getState() != DispatcherRegistrationState.ACTIVE) continue;
            if (!executions.existsByEngineIdAndStateIn(registration.getEngineId(),
                    List.of(cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionState.QUEUED))) continue;
            dispatch(submitters, "submit", registration, registration.getMaxConcurrentSubmissions(), () -> {
                var target = backends.require(registration.getTargetKey());
                if ((!backends.legacy() || registration.getTargetFingerprint() != null)
                        && !Objects.equals(registration.getTargetFingerprint(), target.fingerprint())) return;
                if (backends.readiness(target).ready()) coordinator.admit(registration.getEngineId());
            });
        }
    }

    @Scheduled(scheduler = "dispatcherObservationScheduler", fixedDelayString = "${data-scalpel.dispatcher.observation-poll-interval:5s}")
    public void observe() {
        for (var registration : rotated()) {
            dispatch(observers, "observe", registration, 1, () -> coordinator.observe(registration.getEngineId()));
        }
    }

    @Scheduled(scheduler = "dispatcherCleanupScheduler", fixedDelayString = "${data-scalpel.dispatcher.observation-poll-interval:5s}")
    public void cleanup() {
        for (var registration : rotated()) {
            dispatch(cleaners, "cleanup", registration, 1, () -> coordinator.cleanup(registration.getEngineId()));
        }
    }

    private List<DispatcherRegistration> rotated() {
        var values = new ArrayList<>(registrations.findAll());
        values.sort(Comparator.comparing(DispatcherRegistration::getEngineId));
        if (!values.isEmpty()) Collections.rotate(values, -Math.floorMod(cursor.getAndIncrement(), values.size()));
        return values;
    }

    private void dispatch(ThreadPoolExecutor pool, String lane, DispatcherRegistration registration, int limit, Runnable action) {
        String key = lane + ":" + registration.getEngineId();
        var count = active.computeIfAbsent(key, ignored -> new AtomicInteger());
        if (count.incrementAndGet() > limit) { count.decrementAndGet(); return; }
        try {
            pool.execute(() -> {
                try { action.run(); }
                catch (RuntimeException exception) {
                    // Never log backend messages here: they can contain physical paths or client output.
                    log.warn("Dispatcher engine operation failed: engineId={}, lane={}, exceptionType={}",
                            registration.getEngineId(), lane, exception.getClass().getSimpleName());
                } finally { count.decrementAndGet(); }
            });
        } catch (RejectedExecutionException full) { count.decrementAndGet(); }
    }

    private static ThreadPoolExecutor pool(int size, String name) {
        return new ThreadPoolExecutor(size, size, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<>(),
                Thread.ofPlatform().name(name, 0).daemon(true).factory(), new ThreadPoolExecutor.AbortPolicy());
    }

    @PreDestroy
    public void close() {
        List.of(submitters, observers, cleaners).forEach(ExecutorService::shutdownNow);
    }
}

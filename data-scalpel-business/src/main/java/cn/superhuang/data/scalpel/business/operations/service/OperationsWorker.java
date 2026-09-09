package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.repository.*;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix = "data-scalpel.operations", name = "background-enabled", havingValue = "true", matchIfMissing = true)
public class OperationsWorker {
    private static final Logger log = LoggerFactory.getLogger(OperationsWorker.class);
    private final AlertSignalRepository signals;
    private final AlertIncidentRepository incidents;
    private final AlertDeliveryRepository deliveries;
    private final TaskRunRepository runs;
    private final ComputeEngineRepository engines;
    private final AlertEvaluationService evaluation;
    private final EngineObservationService observations;
    private final AlertDeliveryService deliveryService;
    private final AlertWebhookSender sender;
    private final ExecutorService observationPool = Executors.newFixedThreadPool(4, Thread.ofPlatform().daemon().name("engine-observer-", 0).factory());
    private final ExecutorService deliveryPool = Executors.newFixedThreadPool(4, Thread.ofPlatform().daemon().name("alert-webhook-", 0).factory());
    public OperationsWorker(AlertSignalRepository signals, AlertIncidentRepository incidents, AlertDeliveryRepository deliveries,
                            TaskRunRepository runs, ComputeEngineRepository engines, AlertEvaluationService evaluation,
                            EngineObservationService observations, AlertDeliveryService deliveryService, AlertWebhookSender sender) {
        this.signals = signals; this.incidents = incidents; this.deliveries = deliveries; this.runs = runs; this.engines = engines;
        this.evaluation = evaluation; this.observations = observations; this.deliveryService = deliveryService; this.sender = sender;
    }
    @Scheduled(scheduler = "operationsScheduler", initialDelayString = "15s", fixedDelayString = "${data-scalpel.operations.evaluation-interval:10s}")
    public void evaluate() {
        for (UUID id : signals.due(Instant.now(), PageRequest.of(0, 100))) {
            signals.findById(id).ifPresent(s -> {
                try { evaluation.signal(id, s.getRuleType()); }
                catch (RuntimeException e) { log.error("告警信号处理失败，signalId={}", id, e); evaluation.signalFailed(id); }
            });
        }
        Set<UUID> checkedRuns = new HashSet<>(runs.alertCandidates(PageRequest.of(0, 100)));
        for (UUID id : checkedRuns) safely("运行告警", id, () -> evaluation.run(id));
        for (UUID id : incidents.activeBatch(PageRequest.of(0, 100))) {
            incidents.findById(id).ifPresent(i -> safely("持续告警", id, () -> {
                if (i.getRuleType().engine()) evaluation.engine(i.getSubjectId());
                else if (i.getRunId() != null && !checkedRuns.contains(i.getRunId())) {
                    if (runs.existsById(i.getRunId())) evaluation.run(i.getRunId());
                    else evaluation.missingSource(i);
                }
            }));
        }
    }
    @Scheduled(scheduler = "operationsScheduler", initialDelayString = "10s", fixedDelayString = "5s")
    public void observeEngines() {
        Instant now = Instant.now();
        var work = engines.observationCandidates(now.minusSeconds(30), now, PageRequest.of(0, 20)).stream()
                .map(id -> observationPool.submit(() -> safely("引擎观测", id, () -> {
                    observations.observe(id); evaluation.engine(id);
                }))).toList();
        join(work);
    }
    @Scheduled(scheduler = "operationsScheduler", initialDelayString = "15s", fixedDelayString = "2s")
    public void deliver() {
        var work = deliveries.due(Instant.now(), PageRequest.of(0, 20)).stream().map(id ->
            deliveryPool.submit(() -> safely("Webhook 投递", id, () -> deliveries.findById(id).ifPresent(d -> {
                var attempt = deliveryService.claim(id, d.getRuleType());
                if (attempt != null) sender.send(attempt);
            })))).toList();
        join(work);
    }
    private static void safely(String operation, UUID id, Runnable work) {
        try { work.run(); } catch (RuntimeException e) { log.error("{}失败，id={}", operation, id, e); }
    }
    private static void join(List<? extends Future<?>> work) {
        for (var future : work) {
            try { future.get(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            catch (ExecutionException e) { log.error("运行中心后台工作失败", e.getCause()); }
        }
    }
    @PreDestroy public void close() { observationPool.shutdownNow(); deliveryPool.shutdownNow(); }
}

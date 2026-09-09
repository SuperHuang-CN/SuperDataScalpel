package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.business.operations.repository.*;
import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.*;
import cn.superhuang.data.scalpel.business.compute.domain.*;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;

@Service
public class AlertEvaluationService {
    private final AlertSignalRepository signals;
    private final AlertIncidentRepository incidents;
    private final AlertSilenceRepository silences;
    private final TaskRunRepository runs;
    private final DataTaskRepository tasks;
    private final ComputeEngineRepository engines;
    private final EngineObservationRepository observations;
    private final AlertRuleService rules;
    private final AlertIncidentService lifecycle;
    private final AlertNotificationService notifications;
    private final OperationsProperties properties;
    private final ObjectMapper json;
    public AlertEvaluationService(AlertSignalRepository signals, AlertIncidentRepository incidents, AlertSilenceRepository silences,
                                  TaskRunRepository runs, DataTaskRepository tasks, ComputeEngineRepository engines,
                                  EngineObservationRepository observations, AlertRuleService rules, AlertIncidentService lifecycle,
                                  AlertNotificationService notifications, OperationsProperties properties, ObjectMapper json) {
        this.signals = signals; this.incidents = incidents; this.silences = silences; this.runs = runs; this.tasks = tasks;
        this.engines = engines; this.observations = observations; this.rules = rules; this.lifecycle = lifecycle;
        this.notifications = notifications; this.properties = properties; this.json = json;
    }
    @Transactional
    public void signal(UUID id, AlertRuleType type) {
        rules.lockDefault(type);
        var s = signals.lockById(id).orElse(null);
        if (s == null || s.getStatus() != AlertSignalStatus.PENDING || s.getNextAttemptAt().isAfter(Instant.now())) return;
        var f = json.readValue(s.getFactsJson(), AlertRunFact.class);
        String key = type.name() + ":" + f.runId();
        if (incidents.findByEventKey(key).isEmpty()) {
            var i = create(f.rule(), f.taskId(), f.taskName(), f.runId(), f.engineId(), f.summary(), f.occurredAt());
            i.setEventKey(key); i.setErrorCode(f.errorCode()); i.setDiagnosticId(f.diagnosticId());
            incidents.save(i); lifecycle.action(i, "TRIGGERED", null, f.summary(), null);
            notifications.prepare(i, AlertEventType.TRIGGERED, false);
        }
        s.setStatus(AlertSignalStatus.PROCESSED); s.setLastError(null);
    }
    @Transactional
    public void signalFailed(UUID id) {
        signals.lockById(id).filter(s -> s.getStatus() == AlertSignalStatus.PENDING).ifPresent(s -> {
            s.setAttempts(s.getAttempts() + 1); s.setLastError("告警信号处理失败，后台将继续重试");
            s.setNextAttemptAt(Instant.now().plusSeconds(Math.min(300, 5L * s.getAttempts())));
        });
    }
    @Transactional
    public void run(UUID id) {
        var r = runs.findByIdForUpdate(id).orElse(null);
        if (r == null) return;
        r.markAlertChecked(Instant.now());
        for (var type : List.of(AlertRuleType.QUEUE_TOO_LONG, AlertRuleType.RUN_TOO_LONG)) evaluateRun(r, type);
    }
    private void evaluateRun(TaskRun r, AlertRuleType type) {
        rules.lockDefault(type);
        String key = type.name() + ":" + r.getId();
        var i = incidents.findByActiveKey(key).orElse(null);
        var rule = rules.effective(type, r.getTaskId()).orElse(null);
        var task = tasks.findById(r.getTaskId()).orElse(null);
        if (r.getExecutionMode() != TaskRunExecutionMode.REAL || task == null || rule == null || !rule.getEnabled()
                || type == AlertRuleType.RUN_TOO_LONG && r.getTaskType().isStreaming()) {
            if (i != null) lifecycle.closeCondition(i, "规则停用或对象退出监控", false);
            return;
        }
        boolean active = type == AlertRuleType.QUEUE_TOO_LONG ? r.getStatus() == TaskRunStatus.QUEUED : r.getStatus() == TaskRunStatus.RUNNING;
        Instant start = type == AlertRuleType.QUEUE_TOO_LONG ? r.getQueuedAt() : r.getStartedAt();
        Instant now = Instant.now();
        if (!active || start == null || start.plusSeconds(rule.getThresholdSeconds()).isAfter(now)) {
            if (i != null) {
                boolean recovered = type == AlertRuleType.QUEUE_TOO_LONG
                        ? r.getStartedAt() != null && (r.getStatus() == TaskRunStatus.RUNNING || r.getStatus() == TaskRunStatus.SUCCESS)
                        : r.getStatus() == TaskRunStatus.SUCCESS;
                lifecycle.closeCondition(i, active ? "阈值配置调整后条件不再满足" : "运行状态已变为 " + r.getStatus().name(), recovered);
            }
            return;
        }
        String summary = type == AlertRuleType.QUEUE_TOO_LONG ? "任务排队超过 " + rule.getThresholdSeconds() + " 秒" : "任务执行超过 " + rule.getThresholdSeconds() + " 秒";
        if (i == null) {
            i = create(rules.snapshot(rule), r.getTaskId(), task.getName(), r.getId(), r.getComputeEngineId(), summary, start.plusSeconds(rule.getThresholdSeconds()));
            i.setActiveKey(key); incidents.save(i); lifecycle.action(i, "TRIGGERED", null, summary, null);
            notifications.prepare(i, AlertEventType.TRIGGERED, false);
        }
        observe(i, AlertConditionState.TRIGGERED, now);
    }
    @Transactional
    public void engine(UUID id) {
        var e = engines.findByIdForUpdate(id).orElse(null);
        for (var type : List.of(AlertRuleType.ENGINE_UNREACHABLE, AlertRuleType.ENGINE_NOT_READY)) {
            rules.lockDefault(type);
            var i = incidents.findByActiveKey(type.name() + ":" + id).orElse(null);
            var rule = rules.effective(type, id).orElse(null);
            if (e == null || e.getRegistrationState() != ComputeEngineRegistrationState.ACTIVE || rule == null || !rule.getEnabled()) {
                if (i != null) lifecycle.closeCondition(i, "规则停用或引擎退出监控", false);
                continue;
            }
            var o = observations.findByEngineId(id).orElse(null);
            Instant now = Instant.now();
            if (o == null || o.getObservedAt() == null || o.getObservedAt().plus(properties.observationStaleAfter()).isBefore(now)
                    || o.getState() == EngineObservationState.UNKNOWN
                    || type == AlertRuleType.ENGINE_NOT_READY && (o.getState() != EngineObservationState.REACHABLE || o.getDependenciesReady() == null)) {
                if (i != null) observe(i, AlertConditionState.UNKNOWN, o == null ? null : o.getObservedAt());
                continue;
            }
            boolean triggered = type == AlertRuleType.ENGINE_UNREACHABLE ? o.getState() == EngineObservationState.UNREACHABLE : !o.getDependenciesReady();
            Instant since = type == AlertRuleType.ENGINE_UNREACHABLE ? o.getUnreachableSince() : o.getNotReadySince();
            int healthySamples = type == AlertRuleType.ENGINE_UNREACHABLE ? o.getReachableSamples() : o.getReadySamples();
            if (triggered && since != null && !since.plusSeconds(rule.getThresholdSeconds()).isAfter(now)) {
                if (i == null) {
                    String summary = type == AlertRuleType.ENGINE_UNREACHABLE ? "计算引擎持续不可达" : "计算引擎必需依赖持续未就绪";
                    i = create(rules.snapshot(rule), id, e.getName(), null, id, summary, since.plusSeconds(rule.getThresholdSeconds()));
                    i.setActiveKey(type.name() + ":" + id); incidents.save(i); lifecycle.action(i, "TRIGGERED", null, summary, null);
                    notifications.prepare(i, AlertEventType.TRIGGERED, false);
                }
                observe(i, AlertConditionState.TRIGGERED, o.getObservedAt());
            } else if (i != null && !triggered && healthySamples >= 2) {
                lifecycle.closeCondition(i, "连续两次有效观测确认条件恢复", true);
            } else if (i != null && triggered && since != null
                    && (rule.getConfigurationVersion() != i.getRuleVersion() || !rule.getId().equals(i.getRuleId()))
                    && since.plusSeconds(rule.getThresholdSeconds()).isAfter(now)) {
                lifecycle.closeCondition(i, "阈值配置调整后条件不再满足", false);
            } else if (i != null) {
                // A single healthy sample is insufficient to assert recovery or issue a silence-expiry reminder.
                observe(i, triggered ? AlertConditionState.TRIGGERED : AlertConditionState.UNKNOWN, o.getObservedAt());
            }
        }
    }
    @Transactional
    public void missingSource(AlertIncident snapshot) {
        rules.lockDefault(snapshot.getRuleType());
        incidents.lockById(snapshot.getId()).filter(i -> i.getStatus() != AlertHandlingStatus.CLOSED)
                .ifPresent(i -> lifecycle.closeCondition(i, "来源运行或资源已删除", false));
    }
    private AlertIncident create(AlertRuleSnapshot rule, UUID subjectId, String name, UUID runId, UUID engineId, String summary, Instant occurredAt) {
        var i = new AlertIncident();
        i.setRuleType(rule.type()); i.setRuleId(rule.id()); i.setRuleVersion(rule.version()); i.setSeverity(rule.severity());
        i.setSubjectId(subjectId); i.setSubjectName(name); i.setRunId(runId); i.setEngineId(engineId); i.setSummary(summary);
        i.setOccurredAt(occurredAt); i.setDetectedAt(Instant.now()); i.setLastObservedAt(Instant.now()); i.setLastEvaluatedAt(Instant.now());
        i.setRuleSnapshotJson(json.writeValueAsString(rule)); return i;
    }
    private void observe(AlertIncident i, AlertConditionState state, Instant observedAt) {
        i.setConditionState(state); i.setLastObservedAt(observedAt); i.setLastEvaluatedAt(Instant.now());
        if (state != AlertConditionState.TRIGGERED) return;
        silences.findByScopeKey(AlertRuleService.key(i.getRuleType(), i.getSubjectId()))
                .filter(s -> !s.getUntilAt().isAfter(Instant.now()))
                .filter(s -> s.getReminderSentAt() == null)
                .filter(s -> i.getLastSilenceReminderAt() == null || i.getLastSilenceReminderAt().isBefore(s.getUntilAt()))
                .filter(s -> !s.getUntilAt().isBefore(i.getDetectedAt()))
                .ifPresent(s -> {
                    notifications.prepare(i, AlertEventType.TRIGGERED, true); i.setLastSilenceReminderAt(Instant.now());
                    s.setReminderSentAt(Instant.now());
                    lifecycle.action(i, "SILENCE_EXPIRED", null, "静默到期，条件仍在触发", null);
                });
    }
}

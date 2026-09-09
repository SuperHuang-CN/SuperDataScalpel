package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.business.operations.repository.*;
import cn.superhuang.data.scalpel.business.operations.web.request.*;
import cn.superhuang.data.scalpel.business.operations.web.response.*;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.time.Duration;
import java.util.*;

@Service
public class AlertIncidentService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final AlertIncidentRepository incidents;
    private final AlertActionRepository actions;
    private final AlertSilenceRepository silences;
    private final AlertRuleService rules;
    private final AlertNotificationService notifications;
    private final DataTaskRepository tasks;
    private final ComputeEngineRepository engines;
    private final OperationsAccess access;
    private final SearchEngine search;
    public AlertIncidentService(AlertIncidentRepository incidents, AlertActionRepository actions, AlertSilenceRepository silences,
                                AlertRuleService rules, AlertNotificationService notifications, DataTaskRepository tasks,
                                ComputeEngineRepository engines, OperationsAccess access, SearchEngine search) {
        this.incidents = incidents; this.actions = actions; this.silences = silences; this.rules = rules;
        this.notifications = notifications; this.tasks = tasks; this.engines = engines; this.access = access; this.search = search;
    }
    @Transactional(readOnly = true)
    public PageResponse<AlertIncidentResponse> search(SearchRequest request) {
        var actor = access.actor();
        var p = search.search(request, AlertIncident.class, incidents, (r, q, b) -> r.get("ruleType").in(actor.visibleTypes()));
        var summaries = notificationSummaries(p.getContent().stream().map(AlertIncident::getId).toList());
        return new PageResponse<>(p.map(i -> response(i, summaries.get(i.getId()))).getContent(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
    }
    @Transactional(readOnly = true)
    public AlertIncidentResponse get(UUID id) { return response(requireVisible(id)); }
    @Transactional(readOnly = true)
    public PageResponse<AlertActionResponse> history(UUID id, SearchRequest request) {
        requireVisible(id);
        var p = search.search(request, AlertAction.class, actions, (r, q, b) -> b.equal(r.get("incidentId"), id));
        return new PageResponse<>(p.map(AlertActionResponse::from).getContent(), p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
    }
    @Transactional
    public AlertIncidentResponse acknowledge(UUID id, AlertAcknowledgementRequest request) {
        var actor = access.actor(); actor.require("alert.handle"); var i = lock(id);
        if (i.getStatus() == AlertHandlingStatus.CLOSED) throw conflict("已关闭告警不能再次确认");
        if (i.getStatus() == AlertHandlingStatus.OPEN) {
            i.setStatus(AlertHandlingStatus.ACKNOWLEDGED); action(i, "ACKNOWLEDGED", actor, request.reason(), null);
        }
        return response(i);
    }
    @Transactional
    public AlertIncidentResponse close(UUID id, CloseAlertRequest request) {
        var actor = access.actor(); actor.require("alert.handle"); var i = lock(id);
        if (i.getStatus() != AlertHandlingStatus.CLOSED) {
            if (i.getRuleType().continuous()) throw conflict("持续条件告警由系统根据恢复证据关闭；处理期间可以确认或静默");
            closeCondition(i, request.reason(), false); action(i, "CLOSED", actor, request.reason(), null);
        }
        return response(i);
    }
    @Transactional
    public AlertIncidentResponse silence(UUID id, SilenceAlertRequest request) {
        var actor = access.actor(); actor.require("alert.handle"); var i = lock(id);
        if (i.getStatus() == AlertHandlingStatus.CLOSED) throw conflict("已关闭告警不能设置静默");
        if (request.untilAt().isAfter(Instant.now().plus(Duration.ofDays(30)))) throw conflict("单次静默最多 30 天");
        String key = AlertRuleService.key(i.getRuleType(), i.getSubjectId());
        var s = silences.findByScopeKey(key).orElseGet(AlertSilence::new);
        s.setScopeKey(key); s.setUntilAt(request.untilAt()); s.setActorId(actor.id()); s.setReason(request.reason());
        s.setReminderSentAt(null);
        silences.save(s); action(i, "SILENCED", actor, request.reason(), request.untilAt());
        return response(i);
    }
    @Transactional
    public AlertIncidentResponse unsilence(UUID id) {
        var actor = access.actor(); actor.require("alert.handle"); var i = lock(id);
        silences.findByScopeKey(AlertRuleService.key(i.getRuleType(), i.getSubjectId())).ifPresent(s -> s.setUntilAt(Instant.now()));
        action(i, "UNSILENCED", actor, "取消静默", null); return response(i);
    }
    /** System closure never rewrites the source run status or mistakes administrative closure for recovery. */
    public void closeCondition(AlertIncident i, String reason, boolean recovered) {
        if (i.getStatus() == AlertHandlingStatus.CLOSED) return;
        i.setStatus(AlertHandlingStatus.CLOSED); i.setConditionState(AlertConditionState.CLEARED);
        i.setClosedAt(Instant.now()); i.setCloseReason(reason); i.setActiveKey(null);
        if (i.getRuleType().continuous()) action(i, recovered ? "RECOVERED" : "ENDED", null, reason, null);
        if (recovered) notifications.prepare(i, AlertEventType.RECOVERED, false);
    }
    public void action(AlertIncident i, String action, OperationsAccess.Actor actor, String reason, Instant until) {
        var a = new AlertAction(); a.setIncidentId(i.getId()); a.setAction(action);
        a.setActorId(actor == null ? null : actor.id()); a.setActorName(actor == null ? "系统" : actor.name());
        a.setReason(reason); a.setUntilAt(until); actions.save(a);
    }
    public AlertIncidentResponse response(AlertIncident i) {
        return response(i, notificationSummaries(List.of(i.getId())).get(i.getId()));
    }
    private AlertIncidentResponse response(AlertIncident i, AlertNotificationSummary summary) {
        boolean exists = i.getRuleType().engine() ? engines.existsById(i.getSubjectId()) : tasks.existsById(i.getSubjectId());
        Instant until = silences.findByScopeKey(AlertRuleService.key(i.getRuleType(), i.getSubjectId()))
                .map(AlertSilence::getUntilAt).filter(t -> t.isAfter(Instant.now())).orElse(null);
        return new AlertIncidentResponse(i.getId(), i.getRuleType(), i.getSeverity(), i.getSubjectId(), i.getSubjectName(),
                i.getRunId(), i.getEngineId(), exists, i.getStatus(), i.getConditionState(), i.getSummary(), i.getErrorCode(),
                i.getDiagnosticId(), i.getOccurredAt(), i.getDetectedAt(), i.getLastObservedAt(), i.getClosedAt(), i.getCloseReason(), until, summary);
    }
    private Map<UUID, AlertNotificationSummary> notificationSummaries(List<UUID> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID, long[]> counts = new HashMap<>();
        ids.forEach(id -> counts.put(id, new long[5]));
        entityManager.createQuery("select n.incidentId, count(n) from InAppNotification n where n.incidentId in :ids group by n.incidentId", Object[].class)
                .setParameter("ids", ids).getResultList().forEach(r -> counts.get((UUID) r[0])[0] = ((Number) r[1]).longValue());
        entityManager.createQuery("select d.incidentId, d.status, count(d) from AlertDelivery d where d.incidentId in :ids group by d.incidentId, d.status", Object[].class)
                .setParameter("ids", ids).getResultList().forEach(r -> {
                    int index = switch ((AlertDeliveryStatus) r[1]) { case PENDING, SENDING -> 1; case SENT -> 2; case FAILED -> 3; case SUPPRESSED -> 4; };
                    counts.get((UUID) r[0])[index] += ((Number) r[2]).longValue();
                });
        entityManager.createQuery("select a.incidentId, count(a) from AlertAction a where a.incidentId in :ids and a.action='NOTIFICATION_SUPPRESSED' group by a.incidentId", Object[].class)
                .setParameter("ids", ids).getResultList().forEach(r -> counts.get((UUID) r[0])[4] += ((Number) r[1]).longValue());
        Map<UUID, AlertNotificationSummary> result = new HashMap<>();
        counts.forEach((id, c) -> result.put(id, new AlertNotificationSummary(c[0], c[1], c[2], c[3], c[4])));
        return result;
    }
    private AlertIncident requireVisible(UUID id) {
        var i = incidents.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "告警不存在"));
        access.actor().require(i.getRuleType().permission()); return i;
    }
    private AlertIncident lock(UUID id) {
        var i = requireVisible(id); rules.lockDefault(i.getRuleType());
        entityManager.refresh(i, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        return i;
    }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}

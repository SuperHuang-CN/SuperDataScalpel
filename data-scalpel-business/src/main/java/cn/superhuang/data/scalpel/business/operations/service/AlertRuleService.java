package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.business.operations.repository.*;
import cn.superhuang.data.scalpel.business.operations.web.request.SaveAlertRuleRequest;
import cn.superhuang.data.scalpel.business.operations.web.response.AlertRuleResponse;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.compute.repository.ComputeEngineRepository;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;

@Service
public class AlertRuleService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final AlertRuleRepository rules;
    private final AlertChannelRepository channels;
    private final DataTaskRepository tasks;
    private final ComputeEngineRepository engines;
    private final OperationsAccess access;
    private final SearchEngine search;
    private final ObjectMapper json;
    public AlertRuleService(AlertRuleRepository rules, AlertChannelRepository channels, DataTaskRepository tasks,
                            ComputeEngineRepository engines, OperationsAccess access, SearchEngine search, ObjectMapper json) {
        this.rules = rules; this.channels = channels; this.tasks = tasks; this.engines = engines;
        this.access = access; this.search = search; this.json = json;
    }
    @jakarta.annotation.PostConstruct
    public void initialize() {
        for (var type : AlertRuleType.values()) {
            if (rules.findByScopeKey(key(type, null)).isPresent()) continue;
            var r = new AlertRule();
            r.setScopeKey(key(type, null)); r.setRuleType(type); r.setEnabled(type != AlertRuleType.RUN_TOO_LONG);
            r.setSeverity(type == AlertRuleType.RUN_FAILED || type == AlertRuleType.ENGINE_UNREACHABLE ? AlertSeverity.CRITICAL : AlertSeverity.WARNING);
            r.setThresholdSeconds(type.defaultThreshold()); r.setCooldownSeconds(600); r.setConfigurationVersion(1);
            r.setEnabledAt(Instant.now());
            try { rules.saveAndFlush(r); }
            catch (org.springframework.dao.DataIntegrityViolationException duplicate) {
                if (rules.findByScopeKey(r.getScopeKey()).isEmpty()) throw duplicate;
            }
        }
    }
    public static String key(AlertRuleType type, UUID subjectId) { return type.name() + ":" + (subjectId == null ? "GLOBAL" : subjectId); }
    /** Serializes rule changes, incident creation and notification cooldown for this fixed rule type. */
    public AlertRule lockDefault(AlertRuleType type) {
        return rules.lockByScopeKey(key(type, null)).orElseThrow(() -> new IllegalStateException("告警规则尚未初始化"));
    }
    public Optional<AlertRule> effective(AlertRuleType type, UUID subjectId) {
        return rules.findByScopeKey(key(type, subjectId)).or(() -> rules.findByScopeKey(key(type, null)));
    }
    public AlertRuleSnapshot snapshot(AlertRule r) {
        return new AlertRuleSnapshot(r.getId(), r.getConfigurationVersion(), r.getRuleType(), r.getEnabled(),
                r.getSeverity(), r.getThresholdSeconds(), r.getCooldownSeconds(), ids(r.getUserIdsJson()), ids(r.getChannelIdsJson()),
                channels.findAllById(ids(r.getChannelIdsJson())).stream().collect(java.util.stream.Collectors.toMap(
                        AlertChannel::getId, AlertChannel::getConfigurationVersion)));
    }
    public List<UUID> ids(String value) { return value == null ? List.of() : List.of(json.readValue(value, UUID[].class)); }

    @Transactional(readOnly = true)
    public PageResponse<AlertRuleResponse> search(SearchRequest request) {
        var actor = access.actor(); actor.require("alert.manage");
        var page = search.search(request, AlertRule.class, rules, (r, q, b) -> r.get("ruleType").in(actor.visibleTypes()));
        return new PageResponse<>(page.getContent().stream().map(this::response).toList(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize());
    }
    @Transactional
    public AlertRuleResponse save(UUID id, SaveAlertRuleRequest request) {
        var actor = access.actor(); actor.require("alert.manage"); actor.require(request.ruleType().permission());
        lockDefault(request.ruleType());
        if (request.enabled() && request.ruleType().continuous() && request.thresholdSeconds() < 1) throw bad("持续规则需要大于 0 的阈值");
        if (request.subjectId() != null) {
            boolean exists = request.ruleType().engine() ? engines.existsById(request.subjectId()) : tasks.existsById(request.subjectId());
            if (!exists) throw bad("规则覆盖的任务或引擎不存在");
            if (request.ruleType() == AlertRuleType.RUN_TOO_LONG && tasks.findById(request.subjectId()).orElseThrow().getType().isStreaming())
                throw bad("执行过久规则仅适用于批任务");
        }
        for (UUID user : new HashSet<>(request.userIds())) if (!access.recipientCanView(user, request.ruleType())) throw bad("接收用户已停用或没有来源查看权限");
        for (UUID channel : new HashSet<>(request.channelIds())) if (!channels.existsById(channel)) throw bad("通知渠道不存在");
        String scope = key(request.ruleType(), request.subjectId());
        AlertRule r;
        if (id == null) {
            if (rules.findByScopeKey(scope).isPresent()) throw new ResponseStatusException(HttpStatus.CONFLICT, "该对象已经配置此类规则");
            r = new AlertRule(); r.setScopeKey(scope); r.setRuleType(request.ruleType()); r.setSubjectId(request.subjectId());
        } else {
            r = rules.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "规则不存在"));
            if (!r.getScopeKey().equals(scope)) throw bad("规则类型和覆盖对象不可修改");
        }
        if (request.enabled() && !r.getEnabled()) r.setEnabledAt(Instant.now());
        r.setEnabled(request.enabled()); r.setSeverity(request.severity()); r.setThresholdSeconds(request.thresholdSeconds());
        r.setCooldownSeconds(request.cooldownSeconds()); r.setConfigurationVersion(r.getConfigurationVersion() + 1);
        r.setUserIdsJson(json.writeValueAsString(new LinkedHashSet<>(request.userIds())));
        r.setChannelIdsJson(json.writeValueAsString(new LinkedHashSet<>(request.channelIds())));
        return response(rules.save(r));
    }
    @Transactional
    public void resetOverride(UUID id) {
        var actor = access.actor(); actor.require("alert.manage");
        var r = rules.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "规则不存在"));
        actor.require(r.getRuleType().permission()); lockDefault(r.getRuleType()); entityManager.refresh(r);
        if (r.getSubjectId() == null) throw bad("全局默认规则不能删除");
        rules.delete(r);
    }
    @Transactional
    public AlertRuleResponse setEnabled(UUID id, boolean enabled) {
        var actor = access.actor(); actor.require("alert.manage");
        var r = rules.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "规则不存在"));
        actor.require(r.getRuleType().permission()); lockDefault(r.getRuleType()); entityManager.refresh(r);
        if (enabled && r.getRuleType().continuous() && r.getThresholdSeconds() < 1) throw bad("请先设置告警阈值");
        if (enabled && r.getSubjectId() != null && !(r.getRuleType().engine() ? engines.existsById(r.getSubjectId()) : tasks.existsById(r.getSubjectId()))) throw bad("覆盖对象已删除");
        if (enabled != r.getEnabled()) {
            r.setEnabled(enabled); r.setConfigurationVersion(r.getConfigurationVersion() + 1);
            if (enabled) r.setEnabledAt(Instant.now());
        }
        return response(r);
    }
    public AlertRuleResponse response(AlertRule r) {
        String name = r.getSubjectId() == null ? "全局默认" : r.getRuleType().engine()
                ? engines.findById(r.getSubjectId()).map(e -> e.getName()).orElse("已删除引擎")
                : tasks.findById(r.getSubjectId()).map(t -> t.getName()).orElse("已删除任务");
        var s = snapshot(r);
        return new AlertRuleResponse(r.getId(), s.type(), r.getSubjectId(), name, s.enabled(), s.severity(),
                s.thresholdSeconds(), s.cooldownSeconds(), s.version(), s.userIds(), s.channelIds());
    }
    @Transactional
    public List<AlertRuleResponse> applyOverrides(cn.superhuang.data.scalpel.business.operations.web.request.ApplyAlertOverridesRequest request) {
        var c = request.configuration();
        var actor = access.actor(); actor.require("alert.manage"); actor.require(c.ruleType().permission());
        lockDefault(c.ruleType());
        return request.subjectIds().stream().distinct().map(subjectId -> {
            UUID id = rules.findByScopeKey(key(c.ruleType(), subjectId)).map(AlertRule::getId).orElse(null);
            return save(id, new SaveAlertRuleRequest(c.ruleType(), subjectId, c.enabled(), c.severity(),
                    c.thresholdSeconds(), c.cooldownSeconds(), c.userIds(), c.channelIds()));
        }).toList();
    }
    private static ResponseStatusException bad(String detail) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, detail); }
}

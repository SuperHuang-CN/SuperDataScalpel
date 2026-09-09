package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.business.operations.repository.*;
import cn.superhuang.data.scalpel.business.operations.web.response.AlertDeliveryResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class AlertDeliveryService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private static final long[] BACKOFF = {5, 30, 120, 600, 1800};
    private final AlertDeliveryRepository deliveries;
    private final AlertChannelRepository channels;
    private final AlertIncidentRepository incidents;
    private final AlertRuleService rules;
    private final AlertNotificationService notifications;
    private final OperationsAccess access;
    private final SearchEngine search;
    public AlertDeliveryService(AlertDeliveryRepository deliveries, AlertChannelRepository channels, AlertIncidentRepository incidents,
                                AlertRuleService rules, AlertNotificationService notifications, OperationsAccess access, SearchEngine search) {
        this.deliveries = deliveries; this.channels = channels; this.incidents = incidents; this.rules = rules;
        this.notifications = notifications; this.access = access; this.search = search;
    }
    @Transactional
    public DeliveryAttempt claim(UUID id, AlertRuleType type) {
        if (type != null) rules.lockDefault(type);
        var d = deliveries.lockById(id).orElse(null);
        Instant now = Instant.now();
        if (d == null || d.getStatus() != AlertDeliveryStatus.PENDING && d.getStatus() != AlertDeliveryStatus.SENDING) return null;
        if (d.getStatus() == AlertDeliveryStatus.SENDING && d.getLeaseUntil() != null && d.getLeaseUntil().isAfter(now)
                || d.getStatus() == AlertDeliveryStatus.PENDING && d.getNextAttemptAt().isAfter(now)) return null;
        var c = channels.findById(d.getChannelId()).orElse(null);
        String suppressed = suppression(d, c);
        if (suppressed != null) { d.setStatus(AlertDeliveryStatus.SUPPRESSED); d.setLastError(suppressed); d.setLeaseUntil(null); d.setClaimToken(null); return null; }
        if (d.getIncidentId() != null) {
            var earlier = deliveries.findAllByIncidentIdAndChannelIdOrderBySequenceAsc(d.getIncidentId(), d.getChannelId()).stream()
                    .filter(p -> p.getSequence() < d.getSequence()).toList();
            if (earlier.stream().anyMatch(p -> p.getStatus() == AlertDeliveryStatus.PENDING || p.getStatus() == AlertDeliveryStatus.SENDING)) {
                d.setNextAttemptAt(now.plusSeconds(5)); return null;
            }
            if (d.getEventType() == AlertEventType.RECOVERED && earlier.stream().noneMatch(p -> p.getStatus() == AlertDeliveryStatus.SENT || p.getStatus() == AlertDeliveryStatus.FAILED)) {
                d.setStatus(AlertDeliveryStatus.SUPPRESSED); d.setLastError("触发通知未发送"); return null;
            }
        }
        d.setStatus(AlertDeliveryStatus.SENDING); d.setAttempts(d.getAttempts() + 1);
        d.setClaimToken(UUID.randomUUID()); d.setLeaseUntil(now.plusSeconds(30));
        return new DeliveryAttempt(d.getId(), d.getClaimToken(), c.getUrl(), c.getBearerCiphertext(), c.getHmacCiphertext(), d.getPayloadJson());
    }
    @Transactional
    public void complete(UUID id, UUID token, Integer httpStatus, long durationMillis, boolean retryable, Long retryAfterSeconds, String error) {
        deliveries.lockById(id).filter(d -> d.getStatus() == AlertDeliveryStatus.SENDING && token.equals(d.getClaimToken())).ifPresent(d -> {
            d.setHttpStatus(httpStatus); d.setDurationMillis(durationMillis); d.setLeaseUntil(null); d.setClaimToken(null);
            if (httpStatus != null && httpStatus >= 200 && httpStatus < 300) {
                d.setStatus(AlertDeliveryStatus.SENT); d.setSentAt(Instant.now()); d.setLastError(null);
            } else {
                d.setLastError(error);
                if (retryable && d.getAttempts() <= BACKOFF.length) {
                    d.setStatus(AlertDeliveryStatus.PENDING);
                    long delay = BACKOFF[Math.max(0, d.getAttempts() - 1)];
                    if (retryAfterSeconds != null) delay = Math.max(delay, Math.min(1800, Math.max(0, retryAfterSeconds)));
                    d.setNextAttemptAt(Instant.now().plusSeconds(delay));
                } else d.setStatus(AlertDeliveryStatus.FAILED);
            }
        });
    }
    @Transactional(readOnly = true)
    public PageResponse<AlertDeliveryResponse> search(SearchRequest request) {
        var actor = access.actor();
        var p = search.search(request, AlertDelivery.class, deliveries, (r, q, b) -> actor.has("alert.manage")
                ? b.or(r.get("ruleType").in(actor.visibleTypes()), b.isNull(r.get("ruleType"))) : r.get("ruleType").in(actor.visibleTypes()));
        Map<UUID, String> names = new HashMap<>(); channels.findAllById(p.getContent().stream().map(AlertDelivery::getChannelId).distinct().toList())
                .forEach(c -> names.put(c.getId(), c.getName()));
        return new PageResponse<>(p.map(d -> AlertDeliveryResponse.from(d, names.getOrDefault(d.getChannelId(), "已删除渠道"))).getContent(),
                p.getTotalElements(), p.getTotalPages(), p.getNumber(), p.getSize());
    }
    @Transactional
    public AlertDeliveryResponse retry(UUID id) {
        var actor = access.actor(); actor.require("alert.manage");
        var snapshot = deliveries.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "投递记录不存在"));
        if (snapshot.getRuleType() != null) { actor.require(snapshot.getRuleType().permission()); rules.lockDefault(snapshot.getRuleType()); }
        var d = deliveries.lockById(id).orElseThrow();
        entityManager.refresh(d);
        if (d.getStatus() != AlertDeliveryStatus.FAILED) throw conflict("只能重试失败投递");
        var c = channels.findById(d.getChannelId()).orElse(null);
        String suppressed = suppression(d, c);
        if (suppressed != null) throw conflict(suppressed);
        if (d.getIncidentId() != null && deliveries.findAllByIncidentIdAndChannelIdOrderBySequenceAsc(d.getIncidentId(), d.getChannelId()).stream()
                .anyMatch(later -> later.getSequence() > d.getSequence() && later.getStatus() == AlertDeliveryStatus.SENT)) throw conflict("恢复通知已发送，不能再重试更早的触发通知");
        d.setStatus(AlertDeliveryStatus.PENDING); d.setAttempts(0); d.setNextAttemptAt(Instant.now()); d.setLastError(null);
        return AlertDeliveryResponse.from(d, c.getName());
    }
    private String suppression(AlertDelivery d, AlertChannel c) {
        if (c == null || !c.getEnabled()) return "通知渠道已停用";
        if (c.getConfigurationVersion() != d.getChannelVersion()) return "渠道配置已变化，旧投递不转发到新目标";
        if (d.getRuleType() == null) {
            // TEST is also revoked when its requesting administrator is no longer authorized.
            return d.getRequestedBy() == null || !access.canUser(d.getRequestedBy(), "alert.manage") ? "测试发起人权限已变化" : null;
        }
        var rule = rules.effective(d.getRuleType(), d.getSubjectId()).orElse(null);
        if (rule == null || !rule.getEnabled()) return "告警规则已停用";
        if (notifications.silent(d.getRuleType(), d.getSubjectId(), Instant.now())) return "对象处于静默期";
        var i = incidents.findById(d.getIncidentId()).orElse(null);
        if (i == null) return "告警已清理";
        if (d.getEventType() == AlertEventType.TRIGGERED && i.getStatus() == AlertHandlingStatus.CLOSED
                && (!i.getRuleType().continuous() || "规则停用或对象退出监控".equals(i.getCloseReason())
                    || "规则停用或引擎退出监控".equals(i.getCloseReason()) || "阈值配置调整后条件不再满足".equals(i.getCloseReason())
                    || "来源运行或资源已删除".equals(i.getCloseReason()))) return "告警已关闭";
        return null;
    }
    private static ResponseStatusException conflict(String detail) { return new ResponseStatusException(HttpStatus.CONFLICT, detail); }
    public record DeliveryAttempt(UUID id, UUID token, String url, String bearerCiphertext, String hmacCiphertext, String payload) {}
}

package cn.superhuang.data.scalpel.business.operations.service;

import cn.superhuang.data.scalpel.business.operations.domain.*;
import cn.superhuang.data.scalpel.business.operations.repository.*;
import cn.superhuang.data.scalpel.business.operations.web.response.AlertWebhookPayload;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;

@Service
public class AlertNotificationService {
    private final InAppNotificationRepository notifications;
    private final AlertDeliveryRepository deliveries;
    private final AlertChannelRepository channels;
    private final AlertCooldownRepository cooldowns;
    private final AlertSilenceRepository silences;
    private final AlertActionRepository actions;
    private final OperationsAccess access;
    private final AlertRuleService rules;
    private final OperationsProperties properties;
    private final ObjectMapper json;
    public AlertNotificationService(InAppNotificationRepository notifications, AlertDeliveryRepository deliveries,
                                    AlertChannelRepository channels, AlertCooldownRepository cooldowns, AlertSilenceRepository silences,
                                    AlertActionRepository actions, OperationsAccess access, AlertRuleService rules, OperationsProperties properties, ObjectMapper json) {
        this.notifications = notifications; this.deliveries = deliveries; this.channels = channels; this.cooldowns = cooldowns;
        this.silences = silences; this.actions = actions; this.access = access; this.rules = rules; this.properties = properties; this.json = json;
    }
    public boolean silent(AlertRuleType type, UUID subjectId, Instant now) {
        return silences.findByScopeKey(AlertRuleService.key(type, subjectId)).map(s -> s.getUntilAt().isAfter(now)).orElse(false);
    }
    /** Caller holds the default rule lock; incident and all notifications commit together. */
    public void prepare(AlertIncident incident, AlertEventType event, boolean silenceReminder) {
        Instant now = Instant.now();
        var rule = json.readValue(incident.getRuleSnapshotJson(), AlertRuleSnapshot.class);
        if (rules.effective(rule.type(), incident.getSubjectId()).filter(AlertRule::getEnabled).isEmpty()) {
            suppressed(incident, "告警规则已停用"); return;
        }
        if (silent(rule.type(), incident.getSubjectId(), now)) {
            suppressed(incident, "对象处于静默期");
            return;
        }
        for (UUID userId : rule.userIds()) {
            if (!access.recipientCanView(userId, rule.type())) { suppressed(incident, "站内接收人不可用：" + userId); continue; }
            if (event == AlertEventType.RECOVERED && !notifications.existsByIncidentIdAndUserIdAndEventType(incident.getId(), userId, AlertEventType.TRIGGERED)) continue;
            if (event == AlertEventType.TRIGGERED && !allow(incident, "user:" + userId, rule.cooldownSeconds(), now, silenceReminder)) continue;
            var n = new InAppNotification(); n.setUserId(userId); n.setIncidentId(incident.getId()); n.setRuleType(rule.type());
            n.setEventType(event); n.setSummary(message(incident, event)); notifications.save(n);
        }
        for (UUID channelId : rule.channelIds()) {
            var c = channels.findById(channelId).orElse(null);
            Long version = rule.channelVersions().get(channelId);
            if (c == null || !c.getEnabled() || !Objects.equals(version, c.getConfigurationVersion())) {
                suppressed(incident, "Webhook 渠道已停用或配置已变化：" + channelId); continue;
            }
            if (event == AlertEventType.RECOVERED && deliveries.findAllByIncidentIdAndChannelIdOrderBySequenceAsc(incident.getId(), channelId)
                    .stream().noneMatch(d -> d.getEventType() == AlertEventType.TRIGGERED && d.getStatus() != AlertDeliveryStatus.SUPPRESSED)) continue;
            if (event == AlertEventType.TRIGGERED && !allow(incident, "channel:" + channelId, rule.cooldownSeconds(), now, silenceReminder)) continue;
            var d = new AlertDelivery(); d.setIncidentId(incident.getId()); d.setRuleType(rule.type()); d.setSubjectId(incident.getSubjectId());
            d.setChannelId(channelId); d.setChannelVersion(version); d.setEventType(event);
            d.setSequence(deliveries.findAllByIncidentIdAndChannelIdOrderBySequenceAsc(incident.getId(), channelId)
                    .stream().mapToInt(AlertDelivery::getSequence).max().orElse(0) + 1);
            d.setPayloadJson("{}"); d.setNextAttemptAt(now); deliveries.save(d);
            d.setPayloadJson(json.writeValueAsString(payload(d, incident)));
        }
    }
    public AlertWebhookPayload payload(AlertDelivery d, AlertIncident i) {
        return new AlertWebhookPayload(1, d.getId(), i.getId(), d.getEventType(), d.getSequence(), i.getSeverity(), i.getRuleType(),
                i.getRuleType().engine() ? "ENGINE" : "TASK", i.getSubjectId(), i.getRuleType().engine() ? null : i.getSubjectId(),
                i.getRunId(), i.getEngineId(), i.getSubjectName(), i.getOccurredAt(), i.getDetectedAt(), message(i, d.getEventType()),
                i.getErrorCode(), i.getDiagnosticId(), properties.publicBaseUrl() + "/operations/alerts?incident=" + i.getId());
    }
    private static String message(AlertIncident i, AlertEventType event) {
        return event == AlertEventType.RECOVERED ? i.getRuleType().name() + " 条件已恢复：" + i.getSubjectName()
                : i.getSubjectName() + " · " + i.getSummary();
    }
    private boolean allow(AlertIncident i, String target, int seconds, Instant now, boolean reminder) {
        String key = AlertRuleService.key(i.getRuleType(), i.getSubjectId()) + ":" + target;
        var cooldown = cooldowns.findByScopeKey(key).orElse(null);
        if (!reminder && cooldown != null && cooldown.getNextAllowedAt().isAfter(now)) {
            cooldown.setSuppressedCount(cooldown.getSuppressedCount() + 1);
            suppressed(i, "通知冷却中：" + target); return false;
        }
        if (cooldown == null) { cooldown = new AlertCooldown(); cooldown.setScopeKey(key); }
        cooldown.setNextAllowedAt(now.plusSeconds(seconds)); cooldowns.save(cooldown); return true;
    }
    private void suppressed(AlertIncident i, String reason) {
        var a = new AlertAction(); a.setIncidentId(i.getId()); a.setAction("NOTIFICATION_SUPPRESSED");
        a.setActorName("系统"); a.setReason(reason); actions.save(a);
    }
}

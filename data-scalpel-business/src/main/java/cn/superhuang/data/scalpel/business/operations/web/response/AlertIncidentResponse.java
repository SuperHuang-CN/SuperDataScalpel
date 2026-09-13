package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.operations.domain.*;
import java.time.Instant;
import java.util.UUID;
@Schema(description = "监控规则触发形成的告警事件、当前条件、人工处理和通知状态。")
public record AlertIncidentResponse(
        @Schema(description = "告警事件 UUID。")
        UUID id,
        @Schema(description = "触发本事件的规则类型：运行失败、质检失败、队列或运行超时、引擎不可达或未就绪。")
        AlertRuleType ruleType,
        @Schema(description = "事件严重级别：WARNING 警告或 CRITICAL 严重。")
        AlertSeverity severity,
        @Schema(description = "实际发生本次告警的任务或计算引擎 UUID；即使采用全局默认规则，事件仍绑定具体对象。")
        UUID subjectId,
        @Schema(description = "告警对象在事件形成时保存的显示名称；来源删除后仍保留该快照。")
        String subjectName,
        @Schema(description = "直接触发告警的任务运行 UUID；引擎或全局告警时为空。")
        UUID runId,
        @Schema(description = "直接触发告警的计算引擎 UUID；任务或全局告警时为空。")
        UUID engineId,
        @Schema(description = "产生告警的规则、任务或其他来源对象当前是否仍存在。")
        boolean sourceExists,
        @Schema(description = "人工处理状态：OPEN 待处理，ACKNOWLEDGED 已确认，CLOSED 已关闭；与 conditionState 是否仍触发分开。")
        AlertHandlingStatus status,
        @Schema(description = "被监控条件当前是否仍成立；与人工确认、静默和关闭等处理状态分开。")
        AlertConditionState conditionState,
        @Schema(description = "事件触发原因和当前状态的安全摘要。")
        String summary,
        @Schema(description = "稳定错误码；没有错误时为空。")
        String errorCode,
        @Schema(description = "可关联内部安全诊断记录的 UUID；没有诊断记录时为空。")
        UUID diagnosticId,
        @Schema(description = "被监控事实在来源系统中的发生时间，ISO-8601 UTC 时间戳；可能早于 detectedAt。")
        Instant occurredAt,
        @Schema(description = "告警评估器确认并创建该事件的时间，ISO-8601 UTC 时间戳。")
        Instant detectedAt,
        @Schema(description = "最近一次用于更新条件状态的观测时间，ISO-8601 UTC 时间戳；事件型告警通常为形成事件时刻，持续告警在有效观测后更新，证据不足时可能为空。")
        Instant lastObservedAt,
        @Schema(description = "人工关闭或持续条件自动恢复关闭的时间，ISO-8601 UTC 时间戳；status 不是 CLOSED 时为空。")
        Instant closedAt,
        @Schema(description = "事件关闭原因；事件未关闭时为空。")
        String closeReason,
        @Schema(description = "该规则类型与对象范围当前有效静默的截止时间，ISO-8601 UTC 时间戳；未静默或已到期时为空。同对象同类型事件共享静默，且静默只暂停通知。")
        Instant silencedUntil,
        @Schema(description = "该事件的站内通知、Webhook 投递和抑制决策数量汇总。")
        AlertNotificationSummary notifications
) {}

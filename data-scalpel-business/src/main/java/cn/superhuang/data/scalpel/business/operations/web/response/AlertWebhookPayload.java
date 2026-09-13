package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.operations.domain.*;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "投递到 Webhook 告警通道的稳定事件载荷，不包含内部异常堆栈或凭据")

public record AlertWebhookPayload(
        @Schema(description = "Webhook 告警载荷结构版本；当前固定为 1，接收方可据此选择兼容的解析方式。")
        int schemaVersion,
        @Schema(description = "本次投递记录 UUID。")
        UUID deliveryId,
        @Schema(description = "告警事件 UUID。")
        UUID incidentId,
        @Schema(description = "通知事件类型：TRIGGERED 告警触发或静默到期提醒，RECOVERED 持续条件恢复，TEST 渠道测试。")
        AlertEventType eventType,
        @Schema(description = "同一告警事件向同一渠道发送通知的递增序号；渠道测试事件固定为 0。")
        int sequence,
        @Schema(description = "告警严重级别：WARNING 或 CRITICAL。")
        AlertSeverity severity,
        @Schema(description = "触发事件的监控规则类型。")
        AlertRuleType ruleType,
        @Schema(description = "subjectId 所属对象类型：TASK、ENGINE，渠道测试时为 CHANNEL。")
        String subjectType,
        @Schema(description = "发生告警的任务或引擎 UUID；渠道测试时为告警渠道 UUID。")
        UUID subjectId,
        @Schema(description = "任务类告警关联的任务 UUID；引擎告警或渠道测试时为空。")
        UUID taskId,
        @Schema(description = "告警关联的任务运行 UUID；非运行级告警或渠道测试时为空。")
        UUID runId,
        @Schema(description = "告警关联的计算引擎 UUID；非引擎相关告警或渠道测试时为空。")
        UUID engineId,
        @Schema(description = "告警主体的显示名称；渠道测试事件为渠道名称。")
        String subjectName,
        @Schema(description = "被监控事件实际发生时间，ISO-8601 UTC 时间戳。")
        Instant occurredAt,
        @Schema(description = "平台检测并形成告警的时间，ISO-8601 UTC 时间戳。")
        Instant detectedAt,
        @Schema(description = "本次告警触发、恢复或渠道测试事件的可读摘要。")
        String summary,
        @Schema(description = "稳定错误码；没有错误时为空。")
        String errorCode,
        @Schema(description = "可用于关联内部安全诊断记录的 UUID；没有时为空。")
        UUID diagnosticId,
        @Schema(description = "平台内该告警对象的相对详情路径，不包含认证信息。")
        String detailUrl
) {}

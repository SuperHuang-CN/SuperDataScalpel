package cn.superhuang.data.scalpel.business.operations.web.request;

import io.swagger.v3.oas.annotations.media.Schema;
import cn.superhuang.data.scalpel.business.operations.domain.*;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

@Schema(description = "创建或整体修改一条运行监控告警规则及其通知接收范围。")

public record SaveAlertRuleRequest(
        @Schema(description = "告警条件：运行失败、质检失败、排队过久、运行过久、引擎不可达或引擎未就绪。")
        @NotNull AlertRuleType ruleType,
        @Schema(description = "规则作用对象 UUID；引擎类规则为计算引擎，任务类规则为任务；全局规则按类型允许为空。")
        UUID subjectId,
        @Schema(description = "是否启用；false 时不参与后续执行。")
        boolean enabled,
        @Schema(description = "告警严重级别：WARNING 警告或 CRITICAL 严重。")
        @NotNull AlertSeverity severity,
        @Schema(description = "持续条件达到多久后触发告警，单位秒；启用持续规则时必须大于 0。事件型规则保留该字段但不参与即时事件判定。")
        @Min(0) @Max(2592000) int thresholdSeconds,
        @Schema(description = "同一告警规则两次通知之间的冷却时间，单位秒。")
        @Min(0) @Max(86400) int cooldownSeconds,
        @Schema(description = "接收该规则站内通知的启用用户 UUID 列表；用户还必须具备该类来源查看权限，空列表表示不发送站内通知，重复项按首次出现顺序去重。")
        @NotNull @Size(max=100) List<@NotNull UUID> userIds,
        @Schema(description = "发送该规则通知所使用的告警渠道 UUID 列表；保存时只校验渠道存在，停用渠道会在投递时抑制，空列表表示不发送 Webhook，重复项按首次出现顺序去重。")
        @NotNull @Size(max=30) List<@NotNull UUID> channelIds
) {}

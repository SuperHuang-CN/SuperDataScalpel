package cn.superhuang.data.scalpel.business.operations.web.response;

import io.swagger.v3.oas.annotations.media.Schema;import cn.superhuang.data.scalpel.business.operations.domain.*;
import java.util.List;
import java.util.UUID;
@Schema(description = "当前生效的告警规则配置及通知接收范围。")
public record AlertRuleResponse(
        @Schema(description = "告警规则 UUID。")
        UUID id,
        @Schema(description = "告警条件类型：运行失败、质检失败、队列或运行超时、引擎不可达或未就绪。")
        AlertRuleType ruleType,
        @Schema(description = "规则作用的任务或计算引擎 UUID；允许全局应用的规则可为空。")
        UUID subjectId,
        @Schema(description = "规则作用对象的当前显示名称；全局规则或对象已删除时为空。")
        String subjectName,
        @Schema(description = "是否启用；false 时不参与后续执行。")
        boolean enabled,
        @Schema(description = "触发事件时采用的严重级别：WARNING 或 CRITICAL。")
        AlertSeverity severity,
        @Schema(description = "持续条件达到多久后触发告警，单位秒；事件型规则不使用该值进行即时事件判定。")
        int thresholdSeconds,
        @Schema(description = "同一规则类型、对象和接收目标两次触发通知之间的冷却时间，单位秒；静默到期提醒可跳过冷却判断。")
        int cooldownSeconds,
        @Schema(description = "规则配置版本，创建时为 1，每次保存或实际切换启停状态时递增；告警事件以触发时版本固化规则快照。")
        long configurationVersion,
        @Schema(description = "接收该规则站内通知的用户 UUID 列表，按首次配置顺序去重。")
        List<UUID> userIds,
        @Schema(description = "发送该规则 Webhook 通知的渠道 UUID 列表，按首次配置顺序去重；其中可能包含保存后被停用的渠道。")
        List<UUID> channelIds
) {}

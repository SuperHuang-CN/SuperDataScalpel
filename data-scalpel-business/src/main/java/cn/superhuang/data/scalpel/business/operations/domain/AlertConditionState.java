package cn.superhuang.data.scalpel.business.operations.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "持续告警的当前条件状态：TRIGGERED 最近有效观测仍满足异常条件；CLEARED 已有有效恢复证据；UNKNOWN 观测中断或证据不足，不能据此宣称异常仍在或已经恢复。事件型失败通常保持触发事实，由人工处理状态表达后续处置。")
public enum AlertConditionState {
    TRIGGERED, CLEARED, UNKNOWN
}

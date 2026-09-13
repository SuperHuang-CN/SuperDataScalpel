package cn.superhuang.data.scalpel.business.operations.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "团队共享告警的人工处理状态：OPEN 待处理；ACKNOWLEDGED 已有人确认接手；CLOSED 已处理结束或持续条件自动恢复。它独立于个人通知已读状态及 conditionState。")
public enum AlertHandlingStatus {
    OPEN, ACKNOWLEDGED, CLOSED
}

package cn.superhuang.data.scalpel.business.operations.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Webhook 投递生命周期：PENDING 等待领取或重试；SENDING 已领取且请求可能在途；SENT 接收端返回 2xx；FAILED 已结束重试或遇到不可重试错误；SUPPRESSED 因静默、规则停用、渠道停用或版本变化、来源清理、事件顺序等策略未发送。冷却期通常不创建投递记录。")
public enum AlertDeliveryStatus {
    PENDING, SENDING, SENT, FAILED, SUPPRESSED
}

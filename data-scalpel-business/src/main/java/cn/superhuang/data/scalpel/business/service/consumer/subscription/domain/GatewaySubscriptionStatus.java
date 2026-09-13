package cn.superhuang.data.scalpel.business.service.consumer.subscription.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "订阅成员关系在网关中的状态：GRANT_PENDING 正在授权；GRANTED 已确认授权；GRANT_FAILED 最近授权失败；REVOKE_PENDING 正在撤回；REVOKE_FAILED 最近撤回失败。")
public enum GatewaySubscriptionStatus {
    GRANT_PENDING,
    GRANTED,
    GRANT_FAILED,
    REVOKE_PENDING,
    REVOKE_FAILED
}

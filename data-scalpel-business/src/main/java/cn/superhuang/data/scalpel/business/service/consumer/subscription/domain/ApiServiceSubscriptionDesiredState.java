package cn.superhuang.data.scalpel.business.service.consumer.subscription.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The state DataScalpel expects every gateway projection of a subscription to converge to.
 */
@Schema(description = "DataScalpel 对订阅成员关系的本地期望：GRANTED 要求网关保留授权；REVOKED 要求网关移除授权。成功撤回后本地订阅会被删除，因此 REVOKED 通常只在撤回未完成或失败时可见。")
public enum ApiServiceSubscriptionDesiredState {
    GRANTED,
    REVOKED
}

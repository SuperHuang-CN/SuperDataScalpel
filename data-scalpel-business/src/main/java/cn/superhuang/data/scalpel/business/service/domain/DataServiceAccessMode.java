package cn.superhuang.data.scalpel.business.service.domain;

import io.swagger.v3.oas.annotations.media.Schema;

/** Controls whether callers can access a published data service anonymously or only through subscriptions. */
@Schema(description = "数据服务的网关访问方式：PUBLIC 允许匿名访问；SUBSCRIPTION_REQUIRED 要求消费者具备已同步的服务订阅和有效网关凭据。")
public enum DataServiceAccessMode {
    PUBLIC,
    SUBSCRIPTION_REQUIRED
}

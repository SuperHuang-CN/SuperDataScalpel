package cn.superhuang.data.scalpel.business.service.domain;

/** Controls whether callers can access a published data service anonymously or only through subscriptions. */
public enum DataServiceAccessMode {
    PUBLIC,
    SUBSCRIPTION_REQUIRED
}

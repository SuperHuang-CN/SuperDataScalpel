package cn.superhuang.data.scalpel.business.service.consumer.subscription.domain;

/**
 * The state DataScalpel expects every gateway projection of a subscription to converge to.
 */
public enum ApiServiceSubscriptionDesiredState {
    GRANTED,
    REVOKED
}

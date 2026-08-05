package cn.superhuang.data.scalpel.business.service.accesslog.domain;

public enum GatewayAccessIdentityResolutionStatus {
    RESOLVED,
    ANONYMOUS,
    CONSUMER_UNRESOLVED,
    SERVICE_UNRESOLVED,
    IDENTITY_MISMATCH
}

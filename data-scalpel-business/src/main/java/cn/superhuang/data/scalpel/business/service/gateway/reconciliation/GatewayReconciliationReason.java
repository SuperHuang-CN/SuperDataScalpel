package cn.superhuang.data.scalpel.business.service.gateway.reconciliation;

/** Provider-neutral reason for a gateway binding drift. */
public enum GatewayReconciliationReason {
    REMOTE_MISSING,
    UNEXPECTED_REMOTE,
    CONFIG_MISMATCH,
    OWNER_MISMATCH,
    LOCAL_BINDING_MISSING,
    SECRET_MISMATCH
}

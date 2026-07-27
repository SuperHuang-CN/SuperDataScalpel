package cn.superhuang.data.scalpel.business.service.gateway.reconciliation;

/** Last explicitly requested comparison between local desired state and a gateway binding. */
public enum GatewayReconciliationStatus {
    NOT_CHECKED,
    CHECKING,
    IN_SYNC,
    DRIFTED,
    CHECK_FAILED
}

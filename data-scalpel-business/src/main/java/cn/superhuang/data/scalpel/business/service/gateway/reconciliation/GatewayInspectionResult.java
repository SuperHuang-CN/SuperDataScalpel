package cn.superhuang.data.scalpel.business.service.gateway.reconciliation;

/** Result returned by a provider adapter after a read-only gateway inspection. */
public record GatewayInspectionResult(
        GatewayReconciliationStatus status,
        GatewayReconciliationReason reason,
        String message
) {

    public GatewayInspectionResult {
        if (status != GatewayReconciliationStatus.IN_SYNC
                && status != GatewayReconciliationStatus.DRIFTED) {
            throw new IllegalArgumentException("Inspection result must be IN_SYNC or DRIFTED");
        }
        if (status == GatewayReconciliationStatus.IN_SYNC && reason != null) {
            throw new IllegalArgumentException("In-sync inspection cannot have a drift reason");
        }
        if (status == GatewayReconciliationStatus.DRIFTED && reason == null) {
            throw new IllegalArgumentException("Drifted inspection requires a reason");
        }
        message = limit(message, status == GatewayReconciliationStatus.IN_SYNC
                ? "网关状态与本地期望一致"
                : "网关状态与本地期望不一致");
    }

    public static GatewayInspectionResult inSync(String message) {
        return new GatewayInspectionResult(GatewayReconciliationStatus.IN_SYNC, null, message);
    }

    public static GatewayInspectionResult drifted(
            GatewayReconciliationReason reason,
            String message
    ) {
        return new GatewayInspectionResult(GatewayReconciliationStatus.DRIFTED, reason, message);
    }

    private static String limit(String value, String fallback) {
        String normalized = value == null || value.isBlank() ? fallback : value.trim();
        return normalized.substring(0, Math.min(1000, normalized.length()));
    }
}

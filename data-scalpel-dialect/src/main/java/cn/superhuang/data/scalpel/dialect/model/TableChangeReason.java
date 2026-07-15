package cn.superhuang.data.scalpel.dialect.model;

/** Explainable reason for a strategy, risk, or precondition in a table change plan. */
public record TableChangeReason(TableChangeReasonCode code, String message) {
    public TableChangeReason {
        if (code == null) {
            throw new IllegalArgumentException("Change reason code is required");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Change reason message is required");
        }
        message = message.trim();
    }
}

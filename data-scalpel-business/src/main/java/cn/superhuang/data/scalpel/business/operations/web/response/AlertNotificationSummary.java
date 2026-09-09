package cn.superhuang.data.scalpel.business.operations.web.response;

/** Counts of persisted notification records and suppression decisions for this incident. */
public record AlertNotificationSummary(long inApp, long pending, long sent, long failed, long suppressed) {}

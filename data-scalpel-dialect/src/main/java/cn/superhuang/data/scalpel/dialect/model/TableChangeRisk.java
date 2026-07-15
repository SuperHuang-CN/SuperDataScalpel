package cn.superhuang.data.scalpel.dialect.model;

/** Highest expected operational or data risk of a physical-table change. */
public enum TableChangeRisk {
    SAFE(0),
    CAUTION(1),
    DESTRUCTIVE(2);

    private final int severity;

    TableChangeRisk(int severity) {
        this.severity = severity;
    }

    public boolean isAtLeastAsSevereAs(TableChangeRisk other) {
        return severity >= other.severity;
    }
}

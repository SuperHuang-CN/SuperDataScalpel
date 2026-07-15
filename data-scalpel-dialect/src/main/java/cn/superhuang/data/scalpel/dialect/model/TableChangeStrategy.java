package cn.superhuang.data.scalpel.dialect.model;

/** Dialect conclusion for an individual structural change or an aggregated table change plan. */
public enum TableChangeStrategy {
    METADATA_ONLY(0),
    IN_PLACE(1),
    REBUILD_RECOMMENDED(2),
    REBUILD_REQUIRED(3),
    UNSUPPORTED(4);

    private final int restrictiveness;

    TableChangeStrategy(int restrictiveness) {
        this.restrictiveness = restrictiveness;
    }

    public boolean allowsInPlaceExecution() {
        return this == IN_PLACE || this == REBUILD_RECOMMENDED;
    }

    public boolean allowsRebuildExecution() {
        return this == REBUILD_RECOMMENDED || this == REBUILD_REQUIRED;
    }

    public boolean isAtLeastAsRestrictiveAs(TableChangeStrategy other) {
        return restrictiveness >= other.restrictiveness;
    }
}

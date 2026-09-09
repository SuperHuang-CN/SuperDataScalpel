package cn.superhuang.data.scalpel.contract.task;

/** Explicit density-connected semantics; absent options retain the legacy spatial implementation. */
public record SpatialDbscanOptions(Mode mode, String timeColumnName, Long searchDuration, SpatialDurationUnit searchDurationUnit) {
    public enum Mode { LEGACY_SPATIAL, SPATIAL, LINEAR }
    public boolean usesTime() { return mode == Mode.LINEAR; }
}

package cn.superhuang.data.scalpel.contract.task;

/** Coordinates are in the source projected CRS, independently of the bin size display unit. */
public record SpatialPlanarGridOptions(Double originX, Double originY, Extent extent) {
    public enum ExtentMode { DATA_BOUNDS, EXPLICIT_BOUNDS }
    public record Extent(ExtentMode mode, Double minX, Double minY, Double maxX, Double maxY) { }

    public boolean usesExplicitBounds() {
        return extent != null && extent.mode() == ExtentMode.EXPLICIT_BOUNDS;
    }
}

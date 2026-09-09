package cn.superhuang.data.scalpel.contract.task;

/** Optional region source. Inactive table/grid settings remain explicit drafts. */
public record SpatialWithinRegions(
        Mode mode, SpatialBinShape binShape, Double binSize, SpatialDistanceUnit binSizeUnit,
        SpatialPlanarGridOptions planarGrid, String binIdColumnName, String binGeometryColumnName
) {
    public enum Mode { AREA_TABLE, PLANAR_GRID }
    public boolean usesGrid() { return mode == Mode.PLANAR_GRID; }
}

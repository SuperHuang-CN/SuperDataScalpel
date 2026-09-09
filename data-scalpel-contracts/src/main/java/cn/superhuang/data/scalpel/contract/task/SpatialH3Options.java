package cn.superhuang.data.scalpel.contract.task;

/** In size mode, the existing binSize/binSizeUnit supply the requested approximate diameter. */
public record SpatialH3Options(Mode mode, Integer resolution) {
    public enum Mode { RESOLUTION, APPROXIMATE_SIZE }
}

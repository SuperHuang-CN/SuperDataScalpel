package cn.superhuang.data.scalpel.contract.task;

/** Optional, reference-aligned track reset period, distinct from an adjacent-observation gap. */
public record TrackFixedTimeBoundary(
        Integer interval,
        TrackTimeBoundaryUnit unit,
        String referenceTime,
        String timeZone
) {
}

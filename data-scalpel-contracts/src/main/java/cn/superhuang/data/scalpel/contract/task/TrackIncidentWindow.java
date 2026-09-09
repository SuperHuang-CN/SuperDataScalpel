package cn.superhuang.data.scalpel.contract.task;

/** A node-local condition binding over the half-open observation range [start, end). */
public record TrackIncidentWindow(
        String bindingName,
        String sourceColumnName,
        Kind kind,
        Integer startOffset,
        Integer endOffset
) {
    public enum Kind { COUNT, SUM, MEAN, MIN, MAX, FIRST, LAST, STDDEV_POP, VARIANCE_POP }
}

package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record TrackReconstructOptions(
        TrackReconstructSemantics semantics,
        List<String> orderByColumns,
        TrackSplitBoundaryOption splitBoundaryOption,
        TrackSplitExpression splitExpression,
        TrackPathGeometryOptions pathGeometry,
        TrackAreaGeometryOptions areaGeometry
) {
    public TrackReconstructOptions {
        orderByColumns = orderByColumns == null ? List.of() : List.copyOf(orderByColumns);
    }

    public TrackReconstructOptions(TrackReconstructSemantics semantics, List<String> orderByColumns,
            TrackSplitBoundaryOption splitBoundaryOption, TrackSplitExpression splitExpression,
            TrackPathGeometryOptions pathGeometry) {
        this(semantics, orderByColumns, splitBoundaryOption, splitExpression, pathGeometry, null);
    }

    public TrackReconstructOptions(TrackReconstructSemantics semantics, List<String> orderByColumns,
            TrackSplitBoundaryOption splitBoundaryOption, TrackSplitExpression splitExpression) {
        this(semantics, orderByColumns, splitBoundaryOption, splitExpression, null, null);
    }

    public TrackSplitBoundaryOption effectiveBoundaryOption() {
        return splitBoundaryOption == null ? TrackSplitBoundaryOption.GAP : splitBoundaryOption;
    }
}

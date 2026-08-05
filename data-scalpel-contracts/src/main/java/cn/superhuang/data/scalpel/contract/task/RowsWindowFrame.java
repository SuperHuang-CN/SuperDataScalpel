package cn.superhuang.data.scalpel.contract.task;

public record RowsWindowFrame(
        WindowFrameType type,
        RowsFrameBoundary start,
        RowsFrameBoundary end
) {
}

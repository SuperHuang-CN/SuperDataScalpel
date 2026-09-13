package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
@JsonClassDescription("窗口函数的 ROWS Frame；从 start 到 end 定义相对当前行参与计算的包含式记录范围，起点不能晚于终点。")
public record RowsWindowFrame(
        @JsonPropertyDescription("窗口边界类型；当前必须为 ROWS，start 和 end 按分区排序后的行偏移定义范围。")
        WindowFrameType type,
        @JsonPropertyDescription("包含式范围起点；不能使用 UNBOUNDED_FOLLOWING。")
        RowsFrameBoundary start,
        @JsonPropertyDescription("包含式范围终点；不能使用 UNBOUNDED_PRECEDING，且不能早于 start。")
        RowsFrameBoundary end
) {
}

package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
@JsonSubTypes({
        @JsonSubTypes.Type(
                value = RowsFrameBoundary.UnboundedPreceding.class,
                name = "UNBOUNDED_PRECEDING"
        ),
        @JsonSubTypes.Type(value = RowsFrameBoundary.Preceding.class, name = "PRECEDING"),
        @JsonSubTypes.Type(value = RowsFrameBoundary.CurrentRow.class, name = "CURRENT_ROW"),
        @JsonSubTypes.Type(value = RowsFrameBoundary.Following.class, name = "FOLLOWING"),
        @JsonSubTypes.Type(
                value = RowsFrameBoundary.UnboundedFollowing.class,
                name = "UNBOUNDED_FOLLOWING"
        )
})
@JsonClassDescription("窗口函数 ROWS Frame 的一个起止边界；kind 表示无界前方、前移、当前行、后移或无界后方。")
public sealed interface RowsFrameBoundary permits
        RowsFrameBoundary.UnboundedPreceding,
        RowsFrameBoundary.Preceding,
        RowsFrameBoundary.CurrentRow,
        RowsFrameBoundary.Following,
        RowsFrameBoundary.UnboundedFollowing {

    @JsonClassDescription("行窗口起止边界分支：定位到当前分区的第一行。")

    record UnboundedPreceding() implements RowsFrameBoundary {
    }

    @JsonClassDescription("行窗口起止边界分支：位于当前行之前指定行数的位置。")

    record Preceding(
            @JsonPropertyDescription("边界位于当前行之前的行数，范围 1 到 1000000；方向已由 PRECEDING 表达，不使用负数。")
            long offset
    ) implements RowsFrameBoundary {
    }

    @JsonClassDescription("行窗口起止边界分支：当前记录所在行。")

    record CurrentRow() implements RowsFrameBoundary {
    }

    @JsonClassDescription("行窗口起止边界分支：位于当前行之后指定行数的位置。")

    record Following(
            @JsonPropertyDescription("边界位于当前行之后的行数，范围 1 到 1000000；方向已由 FOLLOWING 表达，不使用负数。")
            long offset
    ) implements RowsFrameBoundary {
    }

    @JsonClassDescription("行窗口起止边界分支：定位到当前分区的最后一行。")

    record UnboundedFollowing() implements RowsFrameBoundary {
    }
}

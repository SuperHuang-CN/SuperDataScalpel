package cn.superhuang.data.scalpel.contract.task;

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
public sealed interface RowsFrameBoundary permits
        RowsFrameBoundary.UnboundedPreceding,
        RowsFrameBoundary.Preceding,
        RowsFrameBoundary.CurrentRow,
        RowsFrameBoundary.Following,
        RowsFrameBoundary.UnboundedFollowing {

    record UnboundedPreceding() implements RowsFrameBoundary {
    }

    record Preceding(long offset) implements RowsFrameBoundary {
    }

    record CurrentRow() implements RowsFrameBoundary {
    }

    record Following(long offset) implements RowsFrameBoundary {
    }

    record UnboundedFollowing() implements RowsFrameBoundary {
    }
}

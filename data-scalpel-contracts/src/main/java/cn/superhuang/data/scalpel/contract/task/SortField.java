package cn.superhuang.data.scalpel.contract.task;

public record SortField(
        String columnName,
        SortDirection direction,
        NullOrdering nullOrdering
) {
}

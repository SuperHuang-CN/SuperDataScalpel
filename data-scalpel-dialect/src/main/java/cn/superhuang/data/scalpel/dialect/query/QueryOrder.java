package cn.superhuang.data.scalpel.dialect.query;

public record QueryOrder(String target, QueryOrderTarget targetType, QuerySortDirection direction) {

    public QueryOrder {
        if (target == null || target.isBlank() || targetType == null || direction == null) {
            throw new IllegalArgumentException("Order target, target type and direction are required");
        }
    }
}

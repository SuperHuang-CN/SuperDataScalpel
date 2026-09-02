package cn.superhuang.data.scalpel.dialect.query;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

import java.util.List;

/**
 * Dialect-neutral AST for a whitelisted standard table query.
 * Identifiers in this model must already have been resolved from trusted model metadata.
 */
public record StandardQuery(
        TableIdentifier table,
        List<QueryProjection> projections,
        QueryPredicate filter,
        List<String> groups,
        List<QueryAggregate> aggregates,
        List<QueryOrder> orders,
        int offset,
        int limit,
        boolean returnCount
) {

    public StandardQuery {
        if (table == null) {
            throw new IllegalArgumentException("Table is required");
        }
        projections = projections == null ? List.of() : List.copyOf(projections);
        groups = groups == null ? List.of() : List.copyOf(groups);
        aggregates = aggregates == null ? List.of() : List.copyOf(aggregates);
        orders = orders == null ? List.of() : List.copyOf(orders);
        if (projections.isEmpty() && aggregates.isEmpty()) {
            throw new IllegalArgumentException("At least one projection or aggregate is required");
        }
        if (offset < 0 || limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("Pagination is outside the allowed range");
        }
    }

    public boolean grouped() {
        return !groups.isEmpty() || !aggregates.isEmpty();
    }
}

package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;

/** A validated non-empty logical predicate group. */
public record QueryFilterGroup(
        ConditionConjunction conjunction,
        List<QueryPredicate> conditions
) implements QueryPredicate {

    public QueryFilterGroup {
        if (conjunction == null) {
            throw new IllegalArgumentException("Filter group conjunction is required");
        }
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
        if (conditions.isEmpty() || conditions.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Filter group conditions are required");
        }
    }
}

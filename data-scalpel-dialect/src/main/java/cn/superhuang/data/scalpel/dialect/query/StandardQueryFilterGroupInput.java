package cn.superhuang.data.scalpel.dialect.query;

import java.util.List;

/** A recursive AND/OR group before its leaf fields are resolved. */
public record StandardQueryFilterGroupInput(
        ConditionConjunction conjunction,
        List<StandardQueryPredicateInput> conditions
) implements StandardQueryPredicateInput {

    public StandardQueryFilterGroupInput {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}

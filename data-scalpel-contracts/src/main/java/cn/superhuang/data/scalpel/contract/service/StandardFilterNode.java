package cn.superhuang.data.scalpel.contract.service;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Recursive condition group or leaf predicate in the standard table service V1 protocol. */
public record StandardFilterNode(
        String field,
        @NotNull StandardFilterOperator operator,
        Object value,
        List<@Valid StandardFilterNode> conditions
) {

    public StandardFilterNode {
        conditions = conditions == null ? List.of() : List.copyOf(conditions);
    }
}

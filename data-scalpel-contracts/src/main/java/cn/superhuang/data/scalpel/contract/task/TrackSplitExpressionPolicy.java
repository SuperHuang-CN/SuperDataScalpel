package cn.superhuang.data.scalpel.contract.task;

import java.util.Set;

/** Window membership is assigned by bindings, never by a user-supplied SQL OVER clause. */
public final class TrackSplitExpressionPolicy {
    private TrackSplitExpressionPolicy() { }

    public static FilterSqlExpressionPolicy.Violation findViolation(String expression) {
        return FilterSqlExpressionPolicy.findViolation(expression, Set.of("OVER", "WINDOW"));
    }
}

package cn.superhuang.data.scalpel.dialect.query;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Restricted, parameterized deletion predicate; never accepts a SQL expression. */
public final class JdbcWritePredicate {
    private JdbcWritePredicate() { }
    public static PreparedQuery compile(DatabaseDialect dialect, QueryPredicate predicate, Set<String> columns) {
        List<QueryParameter> values = new ArrayList<>();
        String sql = render(dialect, predicate, columns, values, 1, new int[1]);
        return new PreparedQuery(sql, values);
    }
    private static String render(DatabaseDialect dialect, QueryPredicate predicate, Set<String> columns,
                                 List<QueryParameter> values, int depth, int[] nodes) {
        if (predicate == null || depth > 12 || ++nodes[0] > 256) {
            throw new IllegalArgumentException("覆盖条件为空或超过深度/数量限制");
        }
        if (predicate instanceof QueryFilterGroup group) {
            if (group.conditions().isEmpty()) throw new IllegalArgumentException("覆盖条件组不能为空");
            return group.conditions().stream().map(child -> render(dialect, child, columns, values, depth + 1, nodes))
                    .collect(Collectors.joining(group.conjunction() == ConditionConjunction.AND ? " AND " : " OR ", "(", ")"));
        }
        QueryFilter filter = (QueryFilter) predicate;
        if (!columns.contains(filter.column())) throw new IllegalArgumentException("覆盖条件字段必须参与本次目标映射");
        String column = dialect.quoteIdentifier(filter.column());
        boolean empty = filter.operator() == QueryFilterOperator.IS_NULL || filter.operator() == QueryFilterOperator.IS_NOT_NULL;
        boolean multiple = filter.operator() == QueryFilterOperator.IN || filter.operator() == QueryFilterOperator.NOT_IN;
        int size = filter.values().size();
        if ((empty && size != 0) || (!empty && (size < 1 || size > (multiple ? 100 : 1)))) {
            throw new IllegalArgumentException("覆盖条件值数量无效");
        }
        String operator = switch (filter.operator()) {
            case EQ -> "="; case NE -> "<>"; case GT -> ">"; case GE -> ">=";
            case LT -> "<"; case LE -> "<="; case IN -> "IN"; case NOT_IN -> "NOT IN";
            case IS_NULL -> "IS NULL"; case IS_NOT_NULL -> "IS NOT NULL";
            default -> throw new IllegalArgumentException("覆盖条件不支持该运算符");
        };
        filter.values().forEach(value -> values.add(new QueryParameter(value, filter.valueType())));
        return column + " " + operator + (empty ? "" : multiple
                ? " (" + String.join(",", java.util.Collections.nCopies(size, "?")) + ")" : " ?");
    }
    public static void bind(PreparedStatement statement, List<QueryParameter> parameters) throws SQLException {
        for (int i = 0; i < parameters.size(); i++) {
            Object value = parameters.get(i).value();
            if (value instanceof java.time.Instant instant) value = java.sql.Timestamp.from(instant);
            if (value instanceof byte[] bytes) statement.setBytes(i + 1, bytes);
            else statement.setObject(i + 1, value);
        }
    }
}

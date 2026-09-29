package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.datascalpel.sdk.WriteCondition;
import cn.superhuang.data.scalpel.dialect.query.*;

/** SDK-to-dialect translation without leaking platform contracts into the public SDK. */
final class SdkWriteConditions {
    private SdkWriteConditions() { }
    static QueryPredicate convert(WriteCondition condition) { return convert(condition, 1, new int[1]); }
    private static QueryPredicate convert(WriteCondition condition, int depth, int[] count) {
        if (condition == null) return null;
        if (depth > 12 || ++count[0] > 256) throw new IllegalArgumentException("覆盖条件超过深度/数量限制");
        if (condition instanceof WriteCondition.Group group) {
            return new QueryFilterGroup(group.all() ? ConditionConjunction.AND : ConditionConjunction.OR,
                    group.conditions().stream().map(c -> convert(c, depth + 1, count)).toList());
        }
        var predicate = (WriteCondition.Predicate) condition;
        for (Object value : predicate.values()) {
            if ((value instanceof Float floatValue && !Float.isFinite(floatValue))
                    || (value instanceof Double doubleValue && !Double.isFinite(doubleValue))) {
                throw new IllegalArgumentException("覆盖条件不支持非有限数值");
            }
            if (!(value instanceof String || value instanceof Boolean || value instanceof Byte
                    || value instanceof Short || value instanceof Integer || value instanceof Long
                    || value instanceof Float || value instanceof Double
                    || value instanceof java.math.BigDecimal || value instanceof java.time.LocalDate
                    || value instanceof java.time.LocalDateTime || value instanceof java.time.OffsetDateTime)) {
                throw new IllegalArgumentException("覆盖条件值类型不支持");
            }
        }
        return new QueryFilter(predicate.column(), QueryValueType.STRING,
                QueryFilterOperator.valueOf(predicate.operator().name()), predicate.values());
    }
}

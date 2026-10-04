package cn.superhuang.datascalpel.sdk;

import java.util.List;
import java.util.Objects;

/**
 * 以目标字段表达原子覆盖范围，不接受 SQL 文本。
 * @apiGroup 批处理原子写入
 * @apiMode BATCH
 * @apiNote 条件字段必须参与映射；不支持 Geometry 或 BINARY。值使用 String、布尔、数字、LocalDate、LocalDateTime 或 OffsetDateTime。
 */
public sealed interface WriteCondition permits WriteCondition.Predicate, WriteCondition.Group {
    /** 目标字段比较运算。 */
    enum Operator {
        /** 等于一个值。 */ EQ,
        /** 不等于一个值。 */ NE,
        /** 大于一个值。 */ GT,
        /** 大于或等于一个值。 */ GE,
        /** 小于一个值。 */ LT,
        /** 小于或等于一个值。 */ LE,
        /** 属于给定非空值集合。 */ IN,
        /** 不属于给定非空值集合。 */ NOT_IN,
        /** 字段为 SQL NULL，不传值。 */ IS_NULL,
        /** 字段不是 SQL NULL，不传值。 */ IS_NOT_NULL
    }

    /**
     * 一个目标字段比较条件。
     * @param column 参与本次映射的目标列名。
     * @param operator 比较运算，空值判断不带值，IN 最多 100 个值。
     * @param values 非空标量值列表，SQL NULL 请使用 IS_NULL/IS_NOT_NULL。
     */
    record Predicate(String column, Operator operator, List<Object> values) implements WriteCondition {
        public Predicate {
            Objects.requireNonNull(column); Objects.requireNonNull(operator);
            values = List.copyOf(values);
        }
    }
    /**
     * 组合一组非空条件。
     * @param all true 表示 AND，false 表示 OR。
     * @param conditions 非空子条件，最大深度 12、总节点数 256。
     */
    record Group(boolean all, List<WriteCondition> conditions) implements WriteCondition {
        public Group { conditions = List.copyOf(conditions); }
    }
    /**
     * 创建字段比较。
     * @param column 目标列名。
     * @param operator 比较运算。
     * @param values 比较值，空值判断不传值。
     */
    static WriteCondition compare(String column, Operator operator, Object... values) {
        return new Predicate(column, operator, List.of(values));
    }
    /**
     * 所有条件同时成立。
     * @param conditions 非空子条件数组。
     */
    static WriteCondition allOf(WriteCondition... conditions) { return new Group(true, List.of(conditions)); }
    /**
     * 至少一个条件成立。
     * @param conditions 非空子条件数组。
     */
    static WriteCondition anyOf(WriteCondition... conditions) { return new Group(false, List.of(conditions)); }
}

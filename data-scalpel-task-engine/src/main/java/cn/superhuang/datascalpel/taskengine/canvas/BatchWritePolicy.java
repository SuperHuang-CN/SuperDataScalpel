package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.query.*;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcBatchWriteStrategies;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import java.util.List;
import java.util.Set;

/** Pure compilation policy, shared by Compiler and Runner; never accesses a database. */
public final class BatchWritePolicy {
    private BatchWritePolicy() { }
    public static void validate(BatchWriteOptions options, JdbcWriteMode mode, CanvasExecutionMode executionMode,
                                CanvasJdbcDatabaseType databaseType, SparkCanvasTable mapped, CanvasNodeIssueSink issues) {
        if (options == null) return;
        String path = "configuration.batchWrite";
        if (executionMode != CanvasExecutionMode.BATCH) {
            issues.error("BATCH_WRITE_NOT_SUPPORTED", "原子写入仅支持批处理", path); return;
        }
        if (databaseType == null || !JdbcBatchWriteStrategies.supports(databaseType.name())) {
            issues.error("BATCH_WRITE_NOT_SUPPORTED", "当前数据库未开放原子批写；可明确选择原有直接提交", path); return;
        }
        if (mode != JdbcWriteMode.OVERWRITE && (options.overwriteCondition() != null || options.allowEmptyOverwrite())) {
            issues.error("BATCH_WRITE_INVALID", "非覆盖模式不能保存覆盖条件或空输入清理选项", path); return;
        }
        if (options.overwriteCondition() != null) {
            CanvasPredicateExpressionBuilder.validate(options.overwriteCondition(), mapped, issues, path + ".overwriteCondition");
            if (issues.hasErrors()) return;
            try {
                QueryPredicate predicate = predicate(options.overwriteCondition(), 1, new int[1]);
                Set<String> permitted = mapped.schema().columns().stream()
                        .filter(c -> c.fieldType() != PlatformDataType.GEOMETRY && c.fieldType() != PlatformDataType.BINARY)
                        .map(CanvasColumnSchema::name).collect(java.util.stream.Collectors.toSet());
                permitted.retainAll(Set.of(mapped.dataset().columns()));
                JdbcWritePredicate.compile(BuiltInDialects.registry().require(databaseType.name()), predicate, permitted);
            } catch (RuntimeException invalid) {
                issues.error("BATCH_WRITE_INVALID", "覆盖条件无效：仅支持已映射的标量字段和比较/集合/空值操作", path);
            }
        }
    }
    public static QueryPredicate predicate(CanvasFilterCondition condition) {
        return condition == null ? null : predicate(condition, 1, new int[1]);
    }
    private static QueryPredicate predicate(CanvasFilterCondition condition, int depth, int[] nodes) {
        if (depth > 12 || ++nodes[0] > 256) throw new IllegalArgumentException("条件超过限制");
        if (condition instanceof CanvasFilterGroup group) {
            if (group.operator() == null || group.children() == null) throw new IllegalArgumentException("条件组无效");
            return new QueryFilterGroup(group.operator() == FilterGroupOperator.AND ? ConditionConjunction.AND : ConditionConjunction.OR,
                    group.children().stream().map(c -> predicate(c, depth + 1, nodes)).toList());
        }
        CanvasFieldPredicate filter = (CanvasFieldPredicate) condition;
        QueryFilterOperator operator = switch (filter.operator()) {
            case EQUALS -> QueryFilterOperator.EQ; case NOT_EQUALS -> QueryFilterOperator.NE;
            case GREATER_THAN -> QueryFilterOperator.GT; case GREATER_THAN_OR_EQUALS -> QueryFilterOperator.GE;
            case LESS_THAN -> QueryFilterOperator.LT; case LESS_THAN_OR_EQUALS -> QueryFilterOperator.LE;
            case IN -> QueryFilterOperator.IN; case NOT_IN -> QueryFilterOperator.NOT_IN;
            case IS_NULL -> QueryFilterOperator.IS_NULL; case IS_NOT_NULL -> QueryFilterOperator.IS_NOT_NULL;
            default -> throw new IllegalArgumentException("覆盖条件运算符不支持");
        };
        List<Object> values = filter.values().stream().map(CanvasPredicateExpressionBuilder::literalValue).toList();
        return new QueryFilter(filter.columnName(), QueryValueType.STRING, operator, values);
    }
}

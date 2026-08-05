package cn.superhuang.datascalpel.taskengine.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasFieldPredicate;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterCondition;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterGroup;
import cn.superhuang.data.scalpel.contract.task.CanvasFilterLimits;
import cn.superhuang.data.scalpel.contract.task.CanvasLiteral;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;
import cn.superhuang.data.scalpel.contract.task.FilterGroupOperator;
import cn.superhuang.data.scalpel.contract.task.FilterOperator;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.datascalpel.taskengine.spark.SparkCanvasTable;
import org.apache.spark.sql.Column;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.functions;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;

final class CanvasPredicateExpressionBuilder {

    private CanvasPredicateExpressionBuilder() {
    }

    static void validate(
            CanvasFilterCondition condition,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues,
            String path
    ) {
        validateCondition(condition, source, issues, path, 1, new int[]{0});
    }

    static Column expression(CanvasFilterCondition condition, Dataset<Row> source) {
        return switch (condition) {
            case CanvasFilterGroup group -> {
                Column result = expression(group.children().getFirst(), source);
                for (int index = 1; index < group.children().size(); index++) {
                    Column child = expression(group.children().get(index), source);
                    result = group.operator() == FilterGroupOperator.AND
                            ? result.and(child)
                            : result.or(child);
                }
                yield result;
            }
            case CanvasFieldPredicate predicate -> predicateExpression(predicate, source);
        };
    }

    static boolean validLiteral(CanvasLiteral literal) {
        if (literal == null || literal.dataType() == null || literal.value() == null
                || literal.dataType() == PlatformDataType.GEOMETRY) {
            return false;
        }
        try {
            literalValue(literal);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    static Object literalValue(CanvasLiteral literal) {
        String value = literal.value();
        return switch (literal.dataType()) {
            case BOOLEAN -> {
                if (!"true".equals(value) && !"false".equals(value)) {
                    throw new IllegalArgumentException("Boolean literal must be true or false");
                }
                yield Boolean.valueOf(value);
            }
            case BYTE -> Byte.valueOf(value);
            case SHORT -> Short.valueOf(value);
            case INTEGER -> Integer.valueOf(value);
            case LONG -> Long.valueOf(value);
            case FLOAT -> {
                float parsed = Float.parseFloat(value);
                if (!Float.isFinite(parsed)) {
                    throw new IllegalArgumentException("Float literal must be finite");
                }
                yield parsed;
            }
            case DOUBLE -> {
                double parsed = Double.parseDouble(value);
                if (!Double.isFinite(parsed)) {
                    throw new IllegalArgumentException("Double literal must be finite");
                }
                yield parsed;
            }
            case DECIMAL -> new BigDecimal(value);
            case STRING -> value;
            case BINARY -> Base64.getDecoder().decode(value);
            case DATE -> Date.valueOf(LocalDate.parse(value));
            case TIMESTAMP -> Timestamp.from(OffsetDateTime.parse(value).toInstant());
            case TIMESTAMP_NTZ -> LocalDateTime.parse(value);
            case GEOMETRY -> throw new IllegalArgumentException("Geometry literal is not supported");
        };
    }

    private static void validateCondition(
            CanvasFilterCondition condition,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues,
            String path,
            int depth,
            int[] nodeCount
    ) {
        if (depth > CanvasFilterLimits.MAX_DEPTH) {
            issues.error(
                    "INVALID_FILTER_CONDITION",
                    "筛选条件超过最大嵌套深度 " + CanvasFilterLimits.MAX_DEPTH,
                    path
            );
            return;
        }
        nodeCount[0]++;
        if (nodeCount[0] > CanvasFilterLimits.MAX_CONDITION_NODES) {
            issues.error(
                    "INVALID_FILTER_CONDITION",
                    "筛选条件节点不能超过 " + CanvasFilterLimits.MAX_CONDITION_NODES,
                    path
            );
            return;
        }
        switch (condition) {
            case CanvasFilterGroup group ->
                    validateGroup(group, source, issues, path, depth, nodeCount);
            case CanvasFieldPredicate predicate ->
                    validatePredicate(predicate, source, issues, path);
        }
    }

    private static void validateGroup(
            CanvasFilterGroup group,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues,
            String path,
            int depth,
            int[] nodeCount
    ) {
        if (group.operator() == null) {
            issues.error("INVALID_FILTER_CONDITION", "请选择条件组操作符", path + ".operator");
        }
        if (group.children() == null || group.children().isEmpty()) {
            issues.error("EMPTY_FILTER_GROUP", "条件组至少需要一个条件", path + ".children");
            return;
        }
        for (int index = 0; index < group.children().size(); index++) {
            CanvasFilterCondition child = group.children().get(index);
            String childPath = path + ".children[" + index + "]";
            if (child == null) {
                issues.error("INVALID_FILTER_CONDITION", "筛选条件不能为空", childPath);
                continue;
            }
            validateCondition(child, source, issues, childPath, depth + 1, nodeCount);
        }
    }

    private static void validatePredicate(
            CanvasFieldPredicate predicate,
            SparkCanvasTable source,
            CanvasNodeIssueSink issues,
            String path
    ) {
        CanvasNodeSupport.required(
                predicate.columnName(),
                "请选择字段",
                path + ".columnName",
                issues
        );
        if (source != null
                && !CanvasNodeSupport.blank(predicate.columnName())
                && !CanvasNodeSupport.columns(source.schema()).containsKey(predicate.columnName())) {
            issues.error(
                    "COLUMN_NOT_FOUND",
                    "来源字段不存在：" + predicate.columnName(),
                    path + ".columnName"
            );
        }
        if (predicate.operator() == null) {
            issues.error("INVALID_FILTER_OPERATOR", "请选择筛选操作符", path + ".operator");
            return;
        }
        CanvasColumnSchema predicateColumn = source == null
                ? null : CanvasNodeSupport.columns(source.schema()).get(predicate.columnName());
        if (predicateColumn != null
                && predicateColumn.fieldType() == PlatformDataType.GEOMETRY
                && predicate.operator() != FilterOperator.IS_NULL
                && predicate.operator() != FilterOperator.IS_NOT_NULL) {
            issues.error(
                    "GEOMETRY_FIELD_OPERATION_UNSUPPORTED",
                    "Geometry 字段只支持 IS_NULL 和 IS_NOT_NULL 条件",
                    path + ".operator"
            );
        }
        if (predicate.values() == null) {
            issues.error("INVALID_FILTER_OPERAND_COUNT", "筛选值必须是数组", path + ".values");
            return;
        }
        if (predicate.values().size() > CanvasFilterLimits.MAX_VALUES_PER_PREDICATE) {
            issues.error(
                    "INVALID_FILTER_OPERAND_COUNT",
                    "单个条件的筛选值不能超过 "
                            + CanvasFilterLimits.MAX_VALUES_PER_PREDICATE,
                    path + ".values"
            );
            return;
        }
        int expected = expectedValueCount(predicate.operator());
        boolean validCount = expected < 0
                ? !predicate.values().isEmpty()
                : predicate.values().size() == expected;
        if (!validCount) {
            issues.error(
                    "INVALID_FILTER_OPERAND_COUNT",
                    operandCountMessage(predicate.operator()),
                    path + ".values"
            );
            return;
        }
        PlatformDataType firstType = null;
        for (int index = 0; index < predicate.values().size(); index++) {
            CanvasLiteral literal = predicate.values().get(index);
            String literalPath = path + ".values[" + index + "]";
            if (!validLiteral(literal)) {
                issues.error("INVALID_FILTER_LITERAL", "筛选值格式无效", literalPath);
                continue;
            }
            if (firstType == null) {
                firstType = literal.dataType();
            } else if (literal.dataType() != firstType) {
                issues.error(
                        "INVALID_FILTER_LITERAL",
                        "同一条件中的筛选值类型必须一致",
                        literalPath
                );
            }
            if (stringOperator(predicate.operator())
                    && literal.dataType() != PlatformDataType.STRING) {
                issues.error(
                        "INVALID_FILTER_LITERAL",
                        "字符串操作符只接受 STRING 筛选值",
                        literalPath
                );
            }
        }
    }

    private static int expectedValueCount(FilterOperator operator) {
        return switch (operator) {
            case IS_NULL, IS_NOT_NULL -> 0;
            case IN, NOT_IN -> -1;
            default -> 1;
        };
    }

    private static String operandCountMessage(FilterOperator operator) {
        return switch (operator) {
            case IS_NULL, IS_NOT_NULL -> "空值判断不能配置筛选值";
            case IN, NOT_IN -> "集合判断至少需要一个筛选值";
            default -> "该操作符必须且只能配置一个筛选值";
        };
    }

    private static boolean stringOperator(FilterOperator operator) {
        return operator == FilterOperator.CONTAINS
                || operator == FilterOperator.STARTS_WITH
                || operator == FilterOperator.ENDS_WITH;
    }

    private static Column predicateExpression(
            CanvasFieldPredicate predicate,
            Dataset<Row> source
    ) {
        Column field = source.col(CanvasNodeSupport.quoteIdentifier(predicate.columnName()));
        return switch (predicate.operator()) {
            case IS_NULL -> field.isNull();
            case IS_NOT_NULL -> field.isNotNull();
            case EQUALS -> field.equalTo(literalColumn(predicate.values().getFirst()));
            case NOT_EQUALS -> field.notEqual(literalColumn(predicate.values().getFirst()));
            case GREATER_THAN -> field.gt(literalColumn(predicate.values().getFirst()));
            case GREATER_THAN_OR_EQUALS -> field.geq(literalColumn(predicate.values().getFirst()));
            case LESS_THAN -> field.lt(literalColumn(predicate.values().getFirst()));
            case LESS_THAN_OR_EQUALS -> field.leq(literalColumn(predicate.values().getFirst()));
            case CONTAINS -> field.contains(predicate.values().getFirst().value());
            case STARTS_WITH -> field.startsWith(predicate.values().getFirst().value());
            case ENDS_WITH -> field.endsWith(predicate.values().getFirst().value());
            case IN, NOT_IN -> {
                Column result = null;
                for (CanvasLiteral value : predicate.values()) {
                    Column comparison = field.equalTo(literalColumn(value));
                    result = result == null ? comparison : result.or(comparison);
                }
                yield predicate.operator() == FilterOperator.NOT_IN
                        ? functions.not(result)
                        : result;
            }
        };
    }

    private static Column literalColumn(CanvasLiteral literal) {
        return functions.lit(literalValue(literal));
    }
}

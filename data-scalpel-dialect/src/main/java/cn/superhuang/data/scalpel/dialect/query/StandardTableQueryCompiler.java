package cn.superhuang.data.scalpel.dialect.query;

import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Compiles a fixed, whitelisted table query into the dialect-neutral {@link StandardQuery} AST.
 * It accepts no SQL text and owns common pagination, filtering, sorting, grouping and value-conversion rules.
 */
public final class StandardTableQueryCompiler {

    private static final Pattern AGGREGATE_ALIAS = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,63}");

    public CompiledStandardTableQuery compile(
            TableIdentifier table,
            List<StandardQueryField> exposedFields,
            StandardQueryInput input,
            StandardQueryLimits limits
    ) {
        if (table == null || input == null || limits == null) {
            throw new IllegalArgumentException("Table, query input and limits are required");
        }
        Map<String, StandardQueryField> fields = fieldsByCode(exposedFields);
        int pageNo = input.pageNo() == null ? 1 : input.pageNo();
        int pageSize = input.pageSize() == null ? limits.defaultPageSize() : input.pageSize();
        if (pageNo < 1) {
            throw invalid("pageNo 必须从 1 开始");
        }
        if (pageSize < 1 || pageSize > limits.maximumPageSize()) {
            throw invalid("pageSize 必须在 1 到 " + limits.maximumPageSize() + " 之间");
        }
        int offset;
        try {
            offset = Math.multiplyExact(pageNo - 1, pageSize);
        } catch (ArithmeticException exception) {
            throw invalid("分页参数过大");
        }
        if (offset > limits.maximumOffset()) {
            throw invalid("分页偏移量不能超过 " + limits.maximumOffset() + "，请缩小查询范围");
        }

        List<QueryProjection> projections = projections(input.columns(), fields);
        List<QueryFilter> filters = filters(input.filters(), fields, limits);
        List<String> groups = groups(input.groups(), fields, limits);
        List<QueryAggregate> aggregates = aggregates(input.aggregates(), fields, projections, groups, limits);
        validateGroupedProjection(projections, groups, aggregates);
        List<QueryOrder> orders = orders(input.orders(), fields, aggregates, groups, projections, limits);
        if (aggregates.isEmpty()) {
            Set<String> orderedColumns = orders.stream()
                    .filter(order -> order.targetType() == QueryOrderTarget.COLUMN)
                    .map(QueryOrder::target)
                    .collect(java.util.stream.Collectors.toSet());
            fields.values().stream().filter(StandardQueryField::primaryKey)
                    .filter(field -> orderedColumns.add(field.physicalColumn()))
                    .forEach(field -> orders.add(new QueryOrder(
                            field.physicalColumn(), QueryOrderTarget.COLUMN, QuerySortDirection.ASC
                    )));
        }

        return new CompiledStandardTableQuery(
                pageNo,
                pageSize,
                new StandardQuery(
                        table,
                        projections,
                        input.conjunction() == null ? ConditionConjunction.AND : input.conjunction(),
                        filters,
                        groups,
                        aggregates,
                        orders,
                        offset,
                        pageSize,
                        input.returnCount() != null && input.returnCount()
                )
        );
    }

    private static List<QueryProjection> projections(List<String> requested, Map<String, StandardQueryField> fields) {
        List<String> selected = requested.isEmpty()
                ? fields.values().stream().filter(StandardQueryField::queryable).map(StandardQueryField::code).toList()
                : requested;
        Set<String> seen = new HashSet<>();
        List<QueryProjection> result = new ArrayList<>();
        for (String code : selected) {
            StandardQueryField field = requireQueryableField(code, fields, "返回字段");
            if (!seen.add(normalizeCode(field.code()))) {
                throw invalid("columns 中包含重复字段：" + field.code());
            }
            result.add(new QueryProjection(field.physicalColumn(), field.code()));
        }
        if (result.isEmpty()) {
            throw invalid("至少选择一个可返回字段");
        }
        return result;
    }

    private static List<QueryFilter> filters(
            List<StandardQueryFilterInput> inputs,
            Map<String, StandardQueryField> fields,
            StandardQueryLimits limits
    ) {
        if (inputs.size() > limits.maximumFilterCount()) {
            throw invalid("filters 数量不能超过 " + limits.maximumFilterCount());
        }
        List<QueryFilter> result = new ArrayList<>();
        for (StandardQueryFilterInput input : inputs) {
            if (input == null || input.operator() == null) {
                throw invalid("过滤条件不能为空");
            }
            StandardQueryField field = requireQueryableField(input.field(), fields, "过滤字段");
            result.add(new QueryFilter(
                    field.physicalColumn(), field.valueType(), input.operator(), values(input, field.valueType(), limits)
            ));
        }
        return result;
    }

    private static List<String> groups(
            List<String> inputs,
            Map<String, StandardQueryField> fields,
            StandardQueryLimits limits
    ) {
        if (inputs.size() > limits.maximumGroupCount()) {
            throw invalid("groups 数量不能超过 " + limits.maximumGroupCount());
        }
        Set<String> seen = new HashSet<>();
        List<String> result = new ArrayList<>();
        for (String input : inputs) {
            StandardQueryField field = requireQueryableField(input, fields, "分组字段");
            if (!seen.add(normalizeCode(field.code()))) {
                throw invalid("groups 中包含重复字段：" + field.code());
            }
            result.add(field.physicalColumn());
        }
        return result;
    }

    private static List<QueryAggregate> aggregates(
            List<StandardQueryAggregateInput> inputs,
            Map<String, StandardQueryField> fields,
            List<QueryProjection> projections,
            List<String> groups,
            StandardQueryLimits limits
    ) {
        if (inputs.size() > limits.maximumAggregateCount()) {
            throw invalid("aggregators 数量不能超过 " + limits.maximumAggregateCount());
        }
        Set<String> aliases = new HashSet<>();
        projections.forEach(projection -> aliases.add(projection.alias()));
        List<QueryAggregate> result = new ArrayList<>();
        for (StandardQueryAggregateInput input : inputs) {
            if (input == null || input.function() == null || input.alias() == null || input.field() == null) {
                throw invalid("聚合条件不能为空");
            }
            String alias = input.alias().trim();
            if (!AGGREGATE_ALIAS.matcher(alias).matches() || !aliases.add(alias)) {
                throw invalid("聚合别名无效或重复：" + input.alias());
            }
            if ("*".equals(input.field())) {
                if (input.function() != AggregateFunction.COUNT) {
                    throw invalid("只有 COUNT 可以聚合 *");
                }
                result.add(new QueryAggregate(input.function(), "*", alias));
                continue;
            }
            StandardQueryField field = requireQueryableField(input.field(), fields, "聚合字段");
            if ((input.function() == AggregateFunction.SUM || input.function() == AggregateFunction.AVG)
                    && !numeric(field.valueType())) {
                throw invalid(input.function() + " 仅支持数值字段");
            }
            result.add(new QueryAggregate(input.function(), field.physicalColumn(), alias));
        }
        return result;
    }

    private static void validateGroupedProjection(
            List<QueryProjection> projections,
            List<String> groups,
            List<QueryAggregate> aggregates
    ) {
        if (aggregates.isEmpty()) {
            return;
        }
        Set<String> groupedColumns = Set.copyOf(groups);
        for (QueryProjection projection : projections) {
            if (!groupedColumns.contains(projection.column())) {
                throw invalid("聚合查询中的普通返回字段必须同时出现在 groups 中");
            }
        }
    }

    private static List<QueryOrder> orders(
            List<StandardQueryOrderInput> inputs,
            Map<String, StandardQueryField> fields,
            List<QueryAggregate> aggregates,
            List<String> groups,
            List<QueryProjection> projections,
            StandardQueryLimits limits
    ) {
        if (inputs.size() > limits.maximumOrderCount()) {
            throw invalid("orders 数量不能超过 " + limits.maximumOrderCount());
        }
        Map<String, String> aggregateAliases = new HashMap<>();
        aggregates.forEach(aggregate -> aggregateAliases.put(aggregate.alias(), aggregate.alias()));
        Set<String> groupedColumns = Set.copyOf(groups);
        Set<String> selectedColumns = projections.stream().map(QueryProjection::column).collect(java.util.stream.Collectors.toSet());
        List<QueryOrder> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (StandardQueryOrderInput input : inputs) {
            if (input == null || input.direction() == null) {
                throw invalid("排序条件不能为空");
            }
            String requested = normalizeCode(input.field());
            StandardQueryField field = fields.get(requested);
            if (field != null) {
                if (!field.queryable()) {
                    throw invalid("排序字段不支持查询：" + field.code());
                }
                if (!aggregates.isEmpty() && (!groupedColumns.contains(field.physicalColumn())
                        || !selectedColumns.contains(field.physicalColumn()))) {
                    throw invalid("聚合查询只能按分组字段或聚合别名排序");
                }
                if (!seen.add("column:" + normalizeCode(field.code()))) {
                    throw invalid("orders 中包含重复字段：" + field.code());
                }
                result.add(new QueryOrder(field.physicalColumn(), QueryOrderTarget.COLUMN, input.direction()));
                continue;
            }
            String alias = aggregateAliases.get(input.field());
            if (alias == null) {
                throw invalid("排序字段不存在：" + input.field());
            }
            if (!seen.add("aggregate:" + alias)) {
                throw invalid("orders 中包含重复字段：" + alias);
            }
            result.add(new QueryOrder(alias, QueryOrderTarget.AGGREGATE_ALIAS, input.direction()));
        }
        return result;
    }

    private static List<Object> values(
            StandardQueryFilterInput filter,
            QueryValueType type,
            StandardQueryLimits limits
    ) {
        return switch (filter.operator()) {
            case IS_NULL, IS_NOT_NULL, IS_EMPTY, IS_NOT_EMPTY -> {
                if (filter.value() != null || filter.secondValue() != null || !filter.values().isEmpty()) {
                    throw invalid(filter.operator() + " 不接受值");
                }
                if ((filter.operator() == QueryFilterOperator.IS_EMPTY || filter.operator() == QueryFilterOperator.IS_NOT_EMPTY)
                        && type != QueryValueType.STRING) {
                    throw invalid(filter.operator() + " 仅支持字符串字段");
                }
                yield List.of();
            }
            case IN, NOT_IN -> {
                List<Object> raw = listValues(filter);
                if (raw.isEmpty() || raw.size() > limits.maximumInValues()) {
                    throw invalid(filter.operator() + " 的值数量必须在 1 到 " + limits.maximumInValues() + " 之间");
                }
                yield raw.stream().map(value -> convert(value, type)).toList();
            }
            case BETWEEN, NOT_BETWEEN -> {
                List<Object> raw = listValues(filter);
                if (raw.size() != 2) {
                    throw invalid(filter.operator() + " 必须提供两个值");
                }
                yield raw.stream().map(value -> convert(value, type)).toList();
            }
            default -> {
                if (filter.value() == null || filter.secondValue() != null || !filter.values().isEmpty()) {
                    throw invalid(filter.operator() + " 必须提供一个 value");
                }
                if ((filter.operator() == QueryFilterOperator.LIKE || filter.operator() == QueryFilterOperator.NOT_LIKE)
                        && type != QueryValueType.STRING) {
                    throw invalid(filter.operator() + " 仅支持字符串字段");
                }
                yield List.of(convert(filter.value(), type));
            }
        };
    }

    private static List<Object> listValues(StandardQueryFilterInput filter) {
        if (!filter.values().isEmpty()) {
            if (filter.value() != null || filter.secondValue() != null) {
                throw invalid("values 不能与 value 或 secondValue 同时使用");
            }
            return filter.values();
        }
        if (filter.value() instanceof Collection<?> collection && filter.secondValue() == null) {
            return new ArrayList<>(collection);
        }
        if (filter.value() != null && filter.secondValue() != null) {
            return List.of(filter.value(), filter.secondValue());
        }
        return List.of();
    }

    private static Object convert(Object raw, QueryValueType type) {
        if (raw == null) {
            throw invalid("过滤值不能为空，请使用 is null 或 is not null");
        }
        try {
            return switch (type) {
                case STRING -> String.valueOf(raw);
                case INTEGER -> decimal(raw).intValueExact();
                case LONG -> decimal(raw).longValueExact();
                case DECIMAL -> decimal(raw);
                case BOOLEAN -> booleanValue(raw);
                case DATE -> LocalDate.parse(String.valueOf(raw));
                case DATETIME -> LocalDateTime.parse(String.valueOf(raw));
            };
        } catch (RuntimeException exception) {
            throw invalid("过滤值类型不匹配：" + raw);
        }
    }

    private static BigDecimal decimal(Object raw) {
        return raw instanceof BigDecimal decimal ? decimal : new BigDecimal(String.valueOf(raw));
    }

    private static boolean booleanValue(Object raw) {
        if (raw instanceof Boolean value) {
            return value;
        }
        if ("true".equalsIgnoreCase(String.valueOf(raw))) {
            return true;
        }
        if ("false".equalsIgnoreCase(String.valueOf(raw))) {
            return false;
        }
        throw invalid("布尔字段只接受 true 或 false");
    }

    private static boolean numeric(QueryValueType type) {
        return type == QueryValueType.INTEGER || type == QueryValueType.LONG || type == QueryValueType.DECIMAL;
    }

    private static Map<String, StandardQueryField> fieldsByCode(List<StandardQueryField> fields) {
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("At least one exposed field is required");
        }
        Map<String, StandardQueryField> result = new LinkedHashMap<>();
        for (StandardQueryField field : fields) {
            if (field == null) {
                throw new IllegalArgumentException("Exposed field cannot be null");
            }
            String normalized = normalizeCode(field.code());
            if (result.putIfAbsent(normalized, field) != null) {
                throw new IllegalStateException("部署快照包含重复模型字段：" + field.code());
            }
        }
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private static StandardQueryField requireQueryableField(
            String code,
            Map<String, StandardQueryField> fields,
            String purpose
    ) {
        StandardQueryField field = fields.get(normalizeCode(code));
        if (field == null) {
            throw invalid("字段未在当前模型中暴露：" + code);
        }
        if (!field.queryable()) {
            throw invalid(purpose + "不支持该字段：" + field.code());
        }
        return field;
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw invalid("字段不能为空");
        }
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(message);
    }
}

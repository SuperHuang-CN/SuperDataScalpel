package cn.superhuang.data.scalpel.engine.query;

import cn.superhuang.data.scalpel.contract.service.ServiceFieldDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.service.StandardAggregator;
import cn.superhuang.data.scalpel.contract.service.StandardFilterNode;
import cn.superhuang.data.scalpel.contract.service.StandardFilterOperator;
import cn.superhuang.data.scalpel.contract.service.StandardOrder;
import cn.superhuang.data.scalpel.contract.service.StandardServiceDefinition;
import cn.superhuang.data.scalpel.contract.service.StandardServiceQueryRequest;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.query.ConditionConjunction;
import cn.superhuang.data.scalpel.dialect.query.QueryFilterOperator;
import cn.superhuang.data.scalpel.dialect.query.QuerySortDirection;
import cn.superhuang.data.scalpel.dialect.query.QueryValueType;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryAggregateInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryField;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryFilterGroupInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryFilterInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryLimits;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryOrderInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryPredicateInput;
import cn.superhuang.data.scalpel.dialect.query.StandardTableQueryCompiler;
import org.springframework.stereotype.Component;

/** Adapts the public service protocol to the shared, dialect-neutral table-query compiler. */
@Component
public class StandardServiceRequestCompiler {

    private static final StandardTableQueryCompiler QUERY_COMPILER = new StandardTableQueryCompiler();

    private final EngineQueryProperties properties;

    public StandardServiceRequestCompiler(EngineQueryProperties properties) {
        this.properties = properties;
    }

    public CompiledServiceRequest compile(StandardServiceDefinition definition, StandardServiceQueryRequest request) {
        try {
            var compiled = QUERY_COMPILER.compile(
                    new TableIdentifier(definition.catalogName(), definition.schemaName(), definition.physicalTableName()),
                    definition.fields().stream().map(StandardServiceRequestCompiler::field).toList(),
                    input(request),
                    new StandardQueryLimits(
                            properties.defaultPageSize(), properties.maximumPageSize(), properties.maximumFilterCount(),
                            properties.maximumFilterDepth(), properties.maximumInValues(), 10, 20, 20,
                            properties.maximumOffset()
                    )
            );
            return new CompiledServiceRequest(compiled.pageNo(), compiled.pageSize(), compiled.query());
        } catch (EngineQueryValidationException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            throw new EngineQueryValidationException(exception.getMessage());
        }
    }

    private static StandardQueryField field(ServiceFieldDefinition definition) {
        if (definition.type() == PlatformDataType.BINARY
                || definition.type() == PlatformDataType.GEOMETRY) {
            return new StandardQueryField(definition.code(), definition.physicalColumn(), null, definition.primaryKey(), false);
        }
        return new StandardQueryField(
                definition.code(), definition.physicalColumn(), valueType(definition.type()), definition.primaryKey(), true
        );
    }

    private static StandardQueryInput input(StandardServiceQueryRequest request) {
        return new StandardQueryInput(
                request.pageNo(),
                request.pageSize(),
                request.fields(),
                rootFilter(request.filter()),
                request.sort().stream().map(StandardServiceRequestCompiler::order).toList(),
                request.groupBy(),
                request.aggregates().stream().map(StandardServiceRequestCompiler::aggregate).toList(),
                Boolean.TRUE.equals(request.returnCount())
        );
    }

    private static StandardQueryFilterGroupInput rootFilter(StandardFilterNode input) {
        if (input == null) {
            return null;
        }
        StandardQueryPredicateInput predicate = filter(input);
        if (predicate instanceof StandardQueryFilterGroupInput group) {
            return group;
        }
        throw new EngineQueryValidationException("filter 根节点必须是 AND 或 OR 条件组");
    }

    private static StandardQueryPredicateInput filter(StandardFilterNode input) {
        if (input == null || input.operator() == null) {
            throw new EngineQueryValidationException("过滤条件必须指定 operator");
        }
        if (input.operator() == StandardFilterOperator.AND || input.operator() == StandardFilterOperator.OR) {
            if (input.field() != null || input.value() != null || input.conditions().isEmpty()) {
                throw new EngineQueryValidationException("条件组只能包含 operator 和非空 conditions");
            }
            return new StandardQueryFilterGroupInput(
                    input.operator() == StandardFilterOperator.OR
                            ? ConditionConjunction.OR
                            : ConditionConjunction.AND,
                    input.conditions().stream().map(StandardServiceRequestCompiler::filter).toList()
            );
        }
        if (input.field() == null || input.field().isBlank() || !input.conditions().isEmpty()) {
            throw new EngineQueryValidationException("叶子条件必须包含 field 和比较 operator，且不能包含 conditions");
        }
        return new StandardQueryFilterInput(input.field(), filterOperator(input.operator()), input.value());
    }

    private static QueryFilterOperator filterOperator(StandardFilterOperator operator) {
        return switch (operator) {
            case AND, OR -> throw new EngineQueryValidationException("逻辑操作符只能用于条件组");
            case GTE -> QueryFilterOperator.GE;
            case LTE -> QueryFilterOperator.LE;
            default -> QueryFilterOperator.valueOf(operator.name());
        };
    }

    private static StandardQueryOrderInput order(StandardOrder input) {
        return new StandardQueryOrderInput(input.field(), QuerySortDirection.valueOf(input.direction().name()));
    }

    private static StandardQueryAggregateInput aggregate(StandardAggregator input) {
        return new StandardQueryAggregateInput(
                cn.superhuang.data.scalpel.dialect.query.AggregateFunction.valueOf(input.function().name()),
                input.field(), input.alias()
        );
    }

    private static QueryValueType valueType(PlatformDataType type) {
        return switch (type) {
            case STRING -> QueryValueType.STRING;
            case BYTE, SHORT, INTEGER -> QueryValueType.INTEGER;
            case LONG -> QueryValueType.LONG;
            case FLOAT, DOUBLE, DECIMAL -> QueryValueType.DECIMAL;
            case BOOLEAN -> QueryValueType.BOOLEAN;
            case DATE -> QueryValueType.DATE;
            case TIMESTAMP, TIMESTAMP_NTZ -> QueryValueType.DATETIME;
            case BINARY -> throw new IllegalArgumentException("BINARY 字段不支持标准服务查询");
            case GEOMETRY -> throw new IllegalArgumentException("Geometry 字段不支持标准服务查询");
        };
    }
}

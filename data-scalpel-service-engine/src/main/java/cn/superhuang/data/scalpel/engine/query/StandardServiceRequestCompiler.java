package cn.superhuang.data.scalpel.engine.query;

import cn.superhuang.data.scalpel.contract.service.ConditionType;
import cn.superhuang.data.scalpel.contract.service.ServiceFieldDefinition;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.service.StandardAggregator;
import cn.superhuang.data.scalpel.contract.service.StandardFilter;
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
import cn.superhuang.data.scalpel.dialect.query.StandardQueryFilterInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryLimits;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryOrderInput;
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
                            properties.maximumInValues(), 10, 20, 20, Integer.MAX_VALUE
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
                request.conditionType() == ConditionType.OR ? ConditionConjunction.OR : ConditionConjunction.AND,
                request.columns(),
                request.filters().stream().map(StandardServiceRequestCompiler::filter).toList(),
                request.orders().stream().map(StandardServiceRequestCompiler::order).toList(),
                request.groups(),
                request.aggregators().stream().map(StandardServiceRequestCompiler::aggregate).toList(),
                request.returnCount() == null || request.returnCount()
        );
    }

    private static StandardQueryFilterInput filter(StandardFilter input) {
        try {
            return new StandardQueryFilterInput(
                    input.name(), QueryFilterOperator.valueOf(StandardFilterOperator.fromValue(input.operator()).name()),
                    input.value(), input.secondValue(), input.values()
            );
        } catch (IllegalArgumentException exception) {
            throw new EngineQueryValidationException(exception.getMessage());
        }
    }

    private static StandardQueryOrderInput order(StandardOrder input) {
        return new StandardQueryOrderInput(input.column(), QuerySortDirection.valueOf(input.direction().name()));
    }

    private static StandardQueryAggregateInput aggregate(StandardAggregator input) {
        return new StandardQueryAggregateInput(
                cn.superhuang.data.scalpel.dialect.query.AggregateFunction.valueOf(input.type().name()),
                input.column(), input.alias()
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

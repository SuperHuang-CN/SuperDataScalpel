package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelDataQueryRequest;
import cn.superhuang.data.scalpel.business.model.web.request.DataModelDataQueryFilterInput;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelDataQueryResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelPreviewResponse;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import cn.superhuang.data.scalpel.dialect.query.ConditionConjunction;
import cn.superhuang.data.scalpel.dialect.query.QueryFilterOperator;
import cn.superhuang.data.scalpel.dialect.query.QuerySortDirection;
import cn.superhuang.data.scalpel.dialect.query.QueryValueType;
import cn.superhuang.data.scalpel.dialect.query.StandardQuery;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryField;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryFilterGroupInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryFilterInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryLimits;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryOrderInput;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryResult;
import cn.superhuang.data.scalpel.dialect.query.StandardTableQueryCompiler;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.time.Duration;

import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.ModelOperationPreparation;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.remoteAccessException;
import static cn.superhuang.data.scalpel.business.model.service.ModelDefinitionService.requireTransactionResult;

@Service
public class DataModelDataQueryService {

    private static final int QUICK_PREVIEW_ROW_COUNT = 50;
    private static final int MODEL_QUERY_MAXIMUM_PAGE_SIZE = 100;
    private static final Duration MODEL_QUERY_TIMEOUT = Duration.ofSeconds(15);
    private static final StandardTableQueryCompiler STANDARD_QUERY_COMPILER = new StandardTableQueryCompiler();
    private static final StandardQueryLimits MODEL_QUERY_LIMITS = new StandardQueryLimits(
            50, MODEL_QUERY_MAXIMUM_PAGE_SIZE, 20, 5, 1_000, 3, 0, 0, 10_000
    );
    private final ModelPhysicalTablePort physicalTablePort;
    private final TransactionTemplate transactionTemplate;
    private final ModelDefinitionService definitions;
    public DataModelDataQueryService(
            ModelPhysicalTablePort physicalTablePort,
            PlatformTransactionManager transactionManager,
            ModelDefinitionService definitions
    ) {
        this.physicalTablePort = physicalTablePort;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.definitions = definitions;
    }


    public DataModelPreviewResponse previewPhysicalTable(UUID id) {
        ModelOperationPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            DataModel model = definitions.requireModel(id);
            return new ModelOperationPreparation(
                    model, model.getUpdatedAt(),
                    definitions.requireModelDataSource(model.getStorageDataSourceId(), true, model.getPhysicalTableMode()),
                    definitions.fieldsFor(id)
            );
        }));
        DataModel model = preparation.model();
        DataSource storage = preparation.storage();
        List<DataModelField> fields = preparation.fields();
        ModelPhysicalTableInspection inspection = requireQueryablePhysicalTable(storage, model, fields);
        StandardQueryInput input = new StandardQueryInput(
                1, QUICK_PREVIEW_ROW_COUNT, List.of(), null,
                defaultClickHouseOrders(storage, model, fields, List.of()), List.of(), List.of(), false
        );
        DataQueryResult result = executeDataQuery(storage, model, fields, inspection.table(), input);
        return DataModelPreviewResponse.from(model, result.fields(), result.rows(), QUICK_PREVIEW_ROW_COUNT, result.hasNext());
    }

    public DataModelDataQueryResponse queryPhysicalTableData(UUID id, DataModelDataQueryRequest request) {
        ModelOperationPreparation preparation = requireTransactionResult(transactionTemplate.execute(status -> {
            DataModel model = definitions.requireModel(id);
            return new ModelOperationPreparation(
                    model, model.getUpdatedAt(),
                    definitions.requireModelDataSource(model.getStorageDataSourceId(), true, model.getPhysicalTableMode()),
                    definitions.fieldsFor(id)
            );
        }));
        DataModel model = preparation.model();
        DataSource storage = preparation.storage();
        List<DataModelField> fields = preparation.fields();
        ModelPhysicalTableInspection inspection = requireQueryablePhysicalTable(storage, model, fields);
        StandardQueryInput input = new StandardQueryInput(
                request.pageNo(), request.pageSize(),
                request.columns(),
                queryFilterGroup(request),
                defaultClickHouseOrders(
                        storage,
                        model,
                        fields,
                        request.orders().stream().map(order -> new StandardQueryOrderInput(
                                order.field(), QuerySortDirection.valueOf(order.direction().name())
                        )).toList()
                ),
                List.of(), List.of(), request.returnCount()
        );
        DataQueryResult result = executeDataQuery(storage, model, fields, inspection.table(), input);
        return new DataModelDataQueryResponse(
                result.fields().stream().map(DataModelDataQueryResponse.Column::from).toList(),
                result.rows(), result.pageNo(), result.pageSize(), result.hasNext(), result.totalCount(), result.stableOrder()
        );
    }

    private DataQueryResult executeDataQuery(
            DataSource storage,
            DataModel model,
            List<DataModelField> fields,
            TableIdentifier table,
            StandardQueryInput input
    ) {
        final cn.superhuang.data.scalpel.dialect.query.CompiledStandardTableQuery compiled;
        try {
            compiled = STANDARD_QUERY_COMPILER.compile(
                    table, fields.stream().map(DataModelDataQueryService::queryField).toList(), input, MODEL_QUERY_LIMITS
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
        int fetchSize = Math.addExact(compiled.pageSize(), 1);
        StandardQuery query = withLimit(compiled.query(), fetchSize);
        StandardQueryResult result;
        try {
            result = physicalTablePort.query(storage, model, query, fetchSize, MODEL_QUERY_TIMEOUT);
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        } catch (UnsupportedOperationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
        }
        boolean hasNext = result.rows().size() > compiled.pageSize();
        List<Map<String, Object>> rows = hasNext ? result.rows().subList(0, compiled.pageSize()) : result.rows();
        Map<String, DataModelField> fieldsByCode = fields.stream()
                .collect(Collectors.toMap(DataModelField::getCode, Function.identity()));
        List<DataModelField> selectedFields = compiled.query().projections().stream()
                .map(projection -> fieldsByCode.get(projection.alias()))
                .filter(Objects::nonNull)
                .toList();
        return new DataQueryResult(
                selectedFields, List.copyOf(rows), compiled.pageNo(), compiled.pageSize(), hasNext,
                result.totalCount(), !compiled.query().orders().isEmpty()
        );
    }

    private ModelPhysicalTableInspection requireQueryablePhysicalTable(
            DataSource storage,
            DataModel model,
            List<DataModelField> fields
    ) {
        ModelPhysicalTableInspection inspection = physicalTablePort.inspect(storage, model, fields);
        if (inspection.state() != PhysicalTableState.MATCHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "物理表未就绪：" + inspection.message());
        }
        return inspection;
    }

    private StandardQueryFilterInput queryFilter(DataModelDataQueryFilterInput input) {
        try {
            return new StandardQueryFilterInput(
                    input.field(), QueryFilterOperator.valueOf(input.operator().trim().toUpperCase(Locale.ROOT)),
                    queryFilterValue(input)
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的过滤运算符：" + input.operator(), exception);
        }
    }

    private StandardQueryFilterGroupInput queryFilterGroup(DataModelDataQueryRequest request) {
        var conditions = request.filters().stream().map(this::queryFilter).toList();
        if (conditions.isEmpty()) {
            return null;
        }
        return new StandardQueryFilterGroupInput(
                "OR".equals(request.conditionType()) ? ConditionConjunction.OR : ConditionConjunction.AND,
                List.copyOf(conditions)
        );
    }

    private static Object queryFilterValue(DataModelDataQueryFilterInput input) {
        if (!input.values().isEmpty()) {
            if (input.value() != null || input.secondValue() != null) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "values 不能与 value 或 secondValue 同时使用"
                );
            }
            return input.values();
        }
        if (input.value() instanceof java.util.Collection<?> && input.secondValue() == null) {
            return input.value();
        }
        if (input.value() != null && input.secondValue() != null) {
            return List.of(input.value(), input.secondValue());
        }
        return input.value();
    }

    private static List<StandardQueryOrderInput> defaultClickHouseOrders(
            DataSource storage,
            DataModel model,
            List<DataModelField> fields,
            List<StandardQueryOrderInput> requestedOrders
    ) {
        if (!requestedOrders.isEmpty() || storage.getType() != DataSourceType.CLICKHOUSE) {
            return requestedOrders;
        }
        Set<String> fieldCodes = fields.stream().map(DataModelField::getCode).collect(Collectors.toSet());
        return model.getClickHouseOrderByColumns().stream()
                .filter(fieldCodes::contains)
                .map(column -> new StandardQueryOrderInput(column, QuerySortDirection.ASC))
                .toList();
    }

    private static StandardQueryField queryField(DataModelField field) {
        if (field.getFieldType() == PlatformDataType.BINARY
                || field.getFieldType() == PlatformDataType.GEOMETRY) {
            return new StandardQueryField(field.getCode(), field.getCode(), null, field.isPrimaryKey(), false);
        }
        return new StandardQueryField(
                field.getCode(), field.getCode(), queryValueType(field.getFieldType()), field.isPrimaryKey(), true
        );
    }

    private static QueryValueType queryValueType(PlatformDataType type) {
        return switch (type) {
            case STRING -> QueryValueType.STRING;
            case BYTE, SHORT, INTEGER -> QueryValueType.INTEGER;
            case LONG -> QueryValueType.LONG;
            case FLOAT, DOUBLE, DECIMAL -> QueryValueType.DECIMAL;
            case BOOLEAN -> QueryValueType.BOOLEAN;
            case DATE -> QueryValueType.DATE;
            case TIMESTAMP, TIMESTAMP_NTZ -> QueryValueType.DATETIME;
            case BINARY -> throw new IllegalArgumentException("BINARY 字段不支持数据查询");
            case GEOMETRY -> throw new IllegalArgumentException("Geometry 字段不支持数据查询");
        };
    }

    private static StandardQuery withLimit(StandardQuery source, int limit) {
        return new StandardQuery(
                source.table(), source.projections(), source.filter(), source.groups(),
                source.aggregates(), source.orders(), source.offset(), limit, source.returnCount()
        );
    }

    private record DataQueryResult(
            List<DataModelField> fields,
            List<Map<String, Object>> rows,
            int pageNo,
            int pageSize,
            boolean hasNext,
            Long totalCount,
            boolean stableOrder
    ) {
    }
}

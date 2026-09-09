package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.cartography.validation.ClassBreakCalculator;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.SpatialDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.SpatialDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.web.request.SpatialStyleFieldProfileRequest;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialStyleFieldProfileResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialStyleFieldResponse;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Performs bounded, read-only profiling for unique-value and class-break renderers. */
@Service
public class SpatialStyleFieldProfileService {

    private static final int QUERY_TIMEOUT_SECONDS = 10;

    private final DataServiceRepository serviceRepository;
    private final SpatialDataServiceDefinitionRepository definitionRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DialectRegistry dialectRegistry;
    private final JdbcConnectionFactory connectionFactory;
    private final TransactionTemplate readTransactionTemplate;

    public SpatialStyleFieldProfileService(
            DataServiceRepository serviceRepository,
            SpatialDataServiceDefinitionRepository definitionRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            DialectRegistry dialectRegistry,
            JdbcConnectionFactory connectionFactory,
            PlatformTransactionManager transactionManager
    ) {
        this.serviceRepository = serviceRepository;
        this.definitionRepository = definitionRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.dialectRegistry = dialectRegistry;
        this.connectionFactory = connectionFactory;
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
    }

    public SpatialStyleFieldProfileResponse profile(UUID serviceId, SpatialStyleFieldProfileRequest request) {
        ProfileSnapshot snapshot = required(readTransactionTemplate.execute(status -> snapshot(serviceId, request)));
        try (Connection connection = connectionFactory.open(snapshot.connectionSpec())) {
            connection.setReadOnly(true);
            Summary summary = summary(
                    connection, snapshot,
                    request.profileType() == SpatialStyleFieldProfileRequest.ProfileType.CLASS_BREAKS
            );
            return request.profileType() == SpatialStyleFieldProfileRequest.ProfileType.UNIQUE_VALUES
                    ? unique(connection, snapshot, summary, request.limit() == null ? 12 : request.limit())
                    : classBreaks(connection, snapshot, summary,
                    request.classificationMethod() == null
                            ? SpatialStyleFieldProfileRequest.ClassificationMethod.EQUAL_INTERVAL
                            : request.classificationMethod(),
                    request.classCount() == null ? 5 : request.classCount());
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (SQLException exception) {
            if ("57014".equals(exception.getSQLState())) {
                throw new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "字段统计超时，请稍后重试或改用手工分级",
                        exception
                );
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "读取空间模型字段统计失败", exception);
        } catch (ClassNotFoundException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "PostgreSQL JDBC 驱动不可用", exception);
        }
    }

    private ProfileSnapshot snapshot(UUID serviceId, SpatialStyleFieldProfileRequest request) {
        DataService service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在"));
        if (service.getType() != DataServiceType.SPATIAL_SERVICE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有空间服务支持制图字段统计");
        }
        SpatialDataServiceDefinition definition = definitionRepository.findByDataServiceId(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "空间服务定义不存在"));
        DataModel model = modelRepository.findById(definition.getModelId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "空间服务关联的模型不存在"));
        List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId());
        DataModelField field = fields.stream().filter(item -> item.getCode().equals(request.fieldCode())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "制图字段不存在或已失效"));
        SpatialStyleDocument.Field coreField = SpatialDataServiceStyleService.coreFields(fields).stream()
                .filter(item -> item.code().equals(field.getCode())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Geometry 字段不能用于分类制图"));
        if (request.profileType() == SpatialStyleFieldProfileRequest.ProfileType.UNIQUE_VALUES
                && !coreField.uniqueValueSupported()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段不支持唯一值分类");
        }
        if (request.profileType() == SpatialStyleFieldProfileRequest.ProfileType.CLASS_BREAKS
                && !coreField.classBreaksSupported()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "字段不支持数值分级");
        }
        DataSource dataSource = dataSourceRepository.findById(model.getStorageDataSourceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "空间模型数据源不存在"));
        if (!dataSource.isEnabled() || dataSource.getType() != DataSourceType.POSTGRESQL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "空间制图字段统计只支持启用的 PostgreSQL 数据源");
        }
        DatabaseDialect dialect = dialectRegistry.require(DataSourceType.POSTGRESQL.name());
        TableIdentifier table = new TableIdentifier(
                model.getCatalogName(), model.getSchemaName(), model.getPhysicalTableName()
        );
        return new ProfileSnapshot(
                coreField, dialect.qualifiedName(table), dialect.quoteIdentifier(field.getCode()),
                dialect.createConnectionSpec(dataSource.getConnection().toJdbcConnectionConfig())
        );
    }

    private static Summary summary(Connection connection, ProfileSnapshot snapshot, boolean numeric) throws SQLException {
        String sql = numeric
                ? "SELECT COUNT(*), COUNT(" + snapshot.column() + "), MIN(" + snapshot.column()
                + "), MAX(" + snapshot.column() + ") FROM " + snapshot.table()
                : "SELECT COUNT(*), COUNT(" + snapshot.column() + ") FROM " + snapshot.table();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return new Summary(0, 0, null, null);
                return new Summary(
                        result.getLong(1), result.getLong(2),
                        numeric ? result.getObject(3) : null,
                        numeric ? result.getObject(4) : null
                );
            }
        }
    }

    private static SpatialStyleFieldProfileResponse unique(
            Connection connection,
            ProfileSnapshot snapshot,
            Summary summary,
            int limit
    ) throws SQLException {
        String sql = "SELECT " + snapshot.column() + ", COUNT(*) AS frequency FROM " + snapshot.table()
                + " WHERE " + snapshot.column() + " IS NOT NULL GROUP BY " + snapshot.column()
                + " ORDER BY frequency DESC, " + snapshot.column() + " ASC LIMIT ?";
        List<SpatialStyleFieldProfileResponse.UniqueValue> values = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        boolean truncated = false;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            statement.setInt(1, limit + 1);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    if (values.size() == limit) {
                        truncated = true;
                        break;
                    }
                    String value = canonical(result.getObject(1), snapshot.field().valueType());
                    if (value.length() > 512) {
                        truncated = true;
                        if (warnings.isEmpty()) warnings.add("超过 512 个字符的唯一值已归入其他");
                        continue;
                    }
                    values.add(new SpatialStyleFieldProfileResponse.UniqueValue(
                            snapshot.field().valueType(), value, result.getLong(2)
                    ));
                }
            }
        }
        return response(snapshot, summary, values, truncated, null, null, List.of(), values.size(), warnings);
    }

    private static SpatialStyleFieldProfileResponse classBreaks(
            Connection connection,
            ProfileSnapshot snapshot,
            Summary summary,
            SpatialStyleFieldProfileRequest.ClassificationMethod method,
            int classCount
    ) throws SQLException {
        List<String> warnings = new ArrayList<>();
        if (summary.nonNullCount() == 0 || summary.minimum() == null || summary.maximum() == null) {
            warnings.add("字段没有可用于分级的非空数值");
            return response(snapshot, summary, List.of(), false, null, null, List.of(), 0, warnings);
        }
        BigDecimal minimum = number(summary.minimum());
        BigDecimal maximum = number(summary.maximum());
        List<BigDecimal> candidates;
        if (minimum.compareTo(maximum) == 0) {
            candidates = List.of();
            warnings.add("字段所有非空值相同，已生成一个分级");
        } else if (method == SpatialStyleFieldProfileRequest.ClassificationMethod.EQUAL_INTERVAL) {
            candidates = ClassBreakCalculator.equalInterval(minimum, maximum, classCount);
        } else {
            candidates = quantiles(connection, snapshot, classCount);
        }
        List<String> breaks = candidates.stream().map(BigDecimal::stripTrailingZeros)
                .distinct().map(BigDecimal::toPlainString).toList();
        if (breaks.size() < candidates.size()) warnings.add("重复分位点已合并，实际分级数少于请求值");
        return response(
                snapshot, summary, List.of(), false,
                minimum.stripTrailingZeros().toPlainString(), maximum.stripTrailingZeros().toPlainString(),
                breaks, breaks.size() + 1, warnings
        );
    }

    private static List<BigDecimal> quantiles(
            Connection connection,
            ProfileSnapshot snapshot,
            int classCount
    ) throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(classCount - 1, "?"));
        String sql = "SELECT percentile_cont(ARRAY[" + placeholders + "]) WITHIN GROUP (ORDER BY "
                + snapshot.column() + ") FROM " + snapshot.table() + " WHERE " + snapshot.column() + " IS NOT NULL";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            for (int index = 1; index < classCount; index++) {
                statement.setDouble(index, (double) index / classCount);
            }
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) return List.of();
                Array array = result.getArray(1);
                if (array == null) return List.of();
                Object raw = array.getArray();
                if (!(raw instanceof Object[] values)) return List.of();
                List<BigDecimal> breaks = new ArrayList<>();
                for (Object value : values) if (value != null) breaks.add(number(value));
                return breaks;
            }
        }
    }

    private static SpatialStyleFieldProfileResponse response(
            ProfileSnapshot snapshot,
            Summary summary,
            List<SpatialStyleFieldProfileResponse.UniqueValue> values,
            boolean truncated,
            String minimum,
            String maximum,
            List<String> breaks,
            int actualClassCount,
            List<String> warnings
    ) {
        SpatialStyleDocument.Field field = snapshot.field();
        return new SpatialStyleFieldProfileResponse(
                new SpatialStyleFieldResponse(
                        field.code(), field.name(), field.dataType(), field.valueType(),
                        field.uniqueValueSupported(), field.classBreaksSupported(), field.labelSupported()
                ),
                summary.totalCount(), summary.nonNullCount(), summary.totalCount() - summary.nonNullCount(),
                values, truncated, minimum, maximum, breaks, actualClassCount, warnings
        );
    }

    private static String canonical(Object value, SpatialStyleDocument.ValueType type) {
        if (value == null) return "";
        return switch (type) {
            case STRING -> value.toString();
            case BOOLEAN -> Boolean.toString(value instanceof Boolean booleanValue
                    ? booleanValue : Boolean.parseBoolean(value.toString()));
            case NUMBER -> number(value).stripTrailingZeros().toPlainString();
        };
    }

    private static BigDecimal number(Object value) {
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static <T> T required(T value) {
        if (value == null) throw new IllegalStateException("事务未返回结果");
        return value;
    }

    private record ProfileSnapshot(
            SpatialStyleDocument.Field field,
            String table,
            String column,
            JdbcConnectionSpec connectionSpec
    ) {
    }

    private record Summary(long totalCount, long nonNullCount, Object minimum, Object maximum) {
    }
}

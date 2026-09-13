package cn.superhuang.data.scalpel.business.dataentry.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryParameter;
import cn.superhuang.data.scalpel.dialect.query.PlatformQueryValueConverter;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcPlatformParameterBinder;
import org.springframework.stereotype.Component;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

@Component
public class JdbcDataEntryPhysicalMutationPort implements DataEntryPhysicalMutationPort {

    private static final int QUERY_TIMEOUT_SECONDS = 15;
    private static final int IMPORT_TIMEOUT_SECONDS = 60;
    private static final int IMPORT_BATCH_SIZE = 500;
    private static final int KEY_QUERY_BATCH_SIZE = 500;

    private final DialectRegistry registry;
    private final JdbcConnectionFactory connectionFactory;

    public JdbcDataEntryPhysicalMutationPort(DialectRegistry registry, JdbcConnectionFactory connectionFactory) {
        this.registry = registry;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public DataEntryPhysicalMutationResult insertBatch(
            DataSource dataSource,
            DataModel model,
            List<DataModelField> fields,
            DataEntryImportRowSource rows
    ) {
        return insertBatch(dataSource, model, fields, rows, null);
    }

    @Override
    public DataEntryPhysicalMutationResult insertBatch(
            DataSource dataSource, DataModel model, List<DataModelField> fields,
            DataEntryImportRowSource rows, DataEntryImportBatchListener listener
    ) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        String sql = insertSql(dialect, dataSource, model, fields);
        try (Connection connection = open(dialect, dataSource, IMPORT_TIMEOUT_SECONDS);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(IMPORT_TIMEOUT_SECONDS);
            ImportBatchState state = new ImportBatchState(
                    statement, fields, dataSource.getType() == DataSourceType.CLICKHOUSE, listener
            );
            try {
                rows.read(state::add);
                state.flush();
                return DataEntryPhysicalMutationResult.succeeded(state.affectedCount);
            } catch (HistoryCallbackException exception) {
                return DataEntryPhysicalMutationResult.incomplete(
                        state.affectedCount, true, "HISTORY_STATUS_SAVE_FAILED",
                        "目标数据库批次已执行，但实际值回读或历史状态保存失败，请人工核对；系统不会自动重试"
                );
            } catch (ImportBatchException exception) {
                return incompleteBatch(
                        state.affectedCount, exception.getCause(),
                        dataSource.getType() == DataSourceType.CLICKHOUSE
                );
            } catch (RuntimeException exception) {
                if (state.affectedCount > 0) {
                    return DataEntryPhysicalMutationResult.incomplete(
                            state.affectedCount, false, "IMPORT_STREAM_ERROR",
                            "批量导入读取失败，已成功批次不会回滚"
                    );
                }
                throw exception;
            }
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public Map<String, Object> queryRecord(DataSource dataSource, DataModel model, List<DataModelField> fields,
            List<DataModelField> businessKeyFields, Map<String, Object> key) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        try (Connection connection = open(dialect, dataSource, QUERY_TIMEOUT_SECONDS)) {
            return queryRecord(connection, dialect, dataSource, model, fields, businessKeyFields, key);
        } catch (DataEntryPhysicalAccessException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public List<Map<String, Object>> queryRecords(DataSource dataSource, DataModel model, List<DataModelField> fields,
            List<DataModelField> businessKeyFields, List<Map<String, Object>> keys) {
        if (keys.isEmpty()) return List.of();
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        String columns = fields.stream().map(field -> dialect.quoteIdentifier(field.getCode()))
                .collect(Collectors.joining(", "));
        String sql = "SELECT " + columns + " FROM " + dialect.qualifiedName(table(dialect, dataSource, model))
                + " WHERE " + keyPredicate(dialect, businessKeyFields, keys.size());
        try (Connection connection = open(dialect, dataSource, QUERY_TIMEOUT_SECONDS);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            JdbcPlatformParameterBinder.bind(statement, keyParameters(businessKeyFields, keys));
            Map<List<String>, Map<String, Object>> byKey = new LinkedHashMap<>();
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int index = 0; index < fields.size(); index++) {
                        row.put(fields.get(index).getCode(), readPlatformValue(resultSet, index + 1, fields.get(index)));
                    }
                    List<String> canonicalKey = canonicalKey(row, businessKeyFields);
                    if (byKey.putIfAbsent(canonicalKey, Collections.unmodifiableMap(row)) != null) {
                        throw new DataEntryPhysicalAccessException(
                                "AFFECTED_COUNT_MISMATCH", "业务主键命中了多条记录", null);
                    }
                }
            }
            List<Map<String, Object>> ordered = new ArrayList<>(keys.size());
            for (Map<String, Object> key : keys) {
                Map<String, Object> row = byKey.get(canonicalKey(key, businessKeyFields));
                if (row == null) throw new DataEntryPhysicalAccessException(
                        "ENTRY_NOT_FOUND", "目标记录回读失败", null);
                ordered.add(row);
            }
            return List.copyOf(ordered);
        } catch (DataEntryPhysicalAccessException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public DataEntryRecordUpdateResult updateRecord(DataSource dataSource, DataModel model,
            List<DataModelField> fields, List<DataModelField> businessKeyFields,
            Map<String, Object> key, Map<String, Object> values) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        List<DataModelField> mutableFields = fields.stream().filter(field -> !field.isPrimaryKey()).toList();
        try (Connection connection = open(dialect, dataSource, QUERY_TIMEOUT_SECONDS)) {
            connection.setAutoCommit(false);
            boolean writeIssued = false;
            boolean commitStarted = false;
            try {
                Map<String, Object> before = queryRecord(connection, dialect, dataSource, model, fields, businessKeyFields, key);
                boolean changed = mutableFields.stream().anyMatch(field -> !Objects.equals(
                        DataEntryValueCanonicalizer.canonical(before.get(field.getCode()), field),
                        DataEntryValueCanonicalizer.canonical(values.get(field.getCode()), field)));
                if (!changed) {
                    connection.rollback();
                    return new DataEntryRecordUpdateResult(false, before, before);
                }
                String assignments = mutableFields.stream().map(field -> dialect.quoteIdentifier(field.getCode()) + " = ?")
                        .collect(Collectors.joining(", "));
                String predicate = businessKeyFields.stream().map(field -> dialect.quoteIdentifier(field.getCode()) + " = ?")
                        .collect(Collectors.joining(" AND "));
                String sql = "UPDATE " + dialect.qualifiedName(table(dialect, dataSource, model)) + " SET "
                        + assignments + " WHERE " + predicate;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
                    List<SqlQueryParameter> parameters = new ArrayList<>();
                    mutableFields.forEach(field -> parameters.add(new SqlQueryParameter(values.get(field.getCode()), type(field))));
                    businessKeyFields.forEach(field -> parameters.add(new SqlQueryParameter(key.get(field.getCode()), type(field))));
                    JdbcPlatformParameterBinder.bind(statement, parameters);
                    writeIssued = true;
                    int affected = statement.executeUpdate();
                    if (affected != 1) throw new DataEntryPhysicalAccessException(
                            "AFFECTED_COUNT_MISMATCH", "更新必须准确命中一条记录", null);
                }
                Map<String, Object> after = queryRecord(connection, dialect, dataSource, model, fields, businessKeyFields, key);
                commitStarted = true;
                connection.commit();
                return new DataEntryRecordUpdateResult(true, before, after);
            } catch (RuntimeException | SQLException exception) {
                boolean rollbackFailed = false;
                try { connection.rollback(); } catch (SQLException rollback) {
                    rollbackFailed = true;
                    exception.addSuppressed(rollback);
                }
                if (exception instanceof RuntimeException runtime) {
                    if (writeIssued && rollbackFailed) {
                        String code = runtime instanceof DataEntryPhysicalAccessException access
                                ? access.code() : "DATABASE_ERROR";
                        throw new DataEntryPhysicalAccessException(code,
                                "更新结果无法确认，请人工核对目标记录，系统不会自动重试", runtime, true);
                    }
                    throw runtime;
                }
                boolean unknown = writeIssued && (rollbackFailed
                        || commitStarted && resultUnknown((SQLException) exception));
                if (unknown) throw new DataEntryPhysicalAccessException(
                        errorCode((SQLException) exception),
                        "更新结果无法确认，请人工核对目标记录，系统不会自动重试", exception, true);
                throw exception;
            }
        } catch (DataEntryPhysicalAccessException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    private Map<String, Object> queryRecord(Connection connection, DatabaseDialect dialect, DataSource dataSource,
            DataModel model, List<DataModelField> fields, List<DataModelField> businessKeyFields,
            Map<String, Object> key) throws SQLException {
        String columns = fields.stream().map(field -> dialect.quoteIdentifier(field.getCode())).collect(Collectors.joining(", "));
        String predicate = businessKeyFields.stream().map(field -> dialect.quoteIdentifier(field.getCode()) + " = ?")
                .collect(Collectors.joining(" AND "));
        String sql = "SELECT " + columns + " FROM " + dialect.qualifiedName(table(dialect, dataSource, model))
                + " WHERE " + predicate;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            List<SqlQueryParameter> parameters = businessKeyFields.stream()
                    .map(field -> new SqlQueryParameter(key.get(field.getCode()), type(field))).toList();
            JdbcPlatformParameterBinder.bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) throw new DataEntryPhysicalAccessException("ENTRY_NOT_FOUND", "目标记录不存在", null);
                Map<String, Object> result = new LinkedHashMap<>();
                for (int index = 0; index < fields.size(); index++) result.put(fields.get(index).getCode(), readPlatformValue(resultSet, index + 1, fields.get(index)));
                if (resultSet.next()) throw new DataEntryPhysicalAccessException("AFFECTED_COUNT_MISMATCH", "业务主键命中了多条记录", null);
                return Collections.unmodifiableMap(result);
            }
        }
    }

    @Override
    public DataEntryPhysicalMutationResult insert(
            DataSource dataSource,
            DataModel model,
            List<DataModelField> fields,
            Map<String, Object> values
    ) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        List<SqlQueryParameter> parameters = fields.stream()
                .map(field -> new SqlQueryParameter(values.get(field.getCode()), type(field)))
                .toList();
        try (Connection connection = open(dialect, dataSource, QUERY_TIMEOUT_SECONDS);
             PreparedStatement statement = connection.prepareStatement(insertSql(dialect, dataSource, model, fields))) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            JdbcPlatformParameterBinder.bind(statement, parameters);
            int affected = statement.executeUpdate();
            if (affected == 1 || affected == Statement.SUCCESS_NO_INFO
                    || (dataSource.getType() == DataSourceType.CLICKHOUSE && affected >= 0)) {
                return DataEntryPhysicalMutationResult.succeeded(1);
            }
            return DataEntryPhysicalMutationResult.incomplete(
                    Math.max(affected, 0), false, "AFFECTED_COUNT_MISMATCH", "新增数据未准确写入一条记录"
            );
        } catch (SQLException exception) {
            if (resultUnknown(exception)) {
                return DataEntryPhysicalMutationResult.incomplete(
                        0, true, errorCode(exception), "新增结果无法确认，请人工核对目标表，系统不会自动重试"
                );
            }
            throw translate(exception);
        }
    }

    @Override
    public DataEntryPhysicalMutationResult deleteBatch(
            DataSource dataSource,
            DataModel model,
            List<DataModelField> businessKeyFields,
            List<Map<String, Object>> keys
    ) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        String predicate = keyPredicate(dialect, businessKeyFields, keys.size());
        String table = dialect.qualifiedName(table(dialect, dataSource, model));
        String sql = dataSource.getType() == DataSourceType.CLICKHOUSE
                ? "ALTER TABLE " + table + " DELETE WHERE " + predicate + " SETTINGS mutations_sync = 2"
                : "DELETE FROM " + table + " WHERE " + predicate;
        try (Connection connection = open(dialect, dataSource, IMPORT_TIMEOUT_SECONDS);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(IMPORT_TIMEOUT_SECONDS);
            JdbcPlatformParameterBinder.bind(statement, keyParameters(businessKeyFields, keys));
            int driverAffected = statement.executeUpdate();
            if (dataSource.getType() != DataSourceType.CLICKHOUSE) {
                if (driverAffected == keys.size()) {
                    return DataEntryPhysicalMutationResult.succeeded(driverAffected);
                }
                return DataEntryPhysicalMutationResult.incomplete(
                        Math.max(driverAffected, 0), false, "AFFECTED_COUNT_MISMATCH",
                        "删除数量与请求数量不一致，已删除记录不会回滚"
                );
            }
        } catch (DataEntryPhysicalAccessException exception) {
            throw exception;
        } catch (SQLException exception) {
            if (resultUnknown(exception)) {
                return DataEntryPhysicalMutationResult.incomplete(
                        0, true, errorCode(exception), "删除结果无法确认，请人工核对目标表，系统不会自动重试"
                );
            }
            throw translate(exception);
        }

        try {
            int remaining = findBusinessKeyMatches(dataSource, model, businessKeyFields, keys).size();
            int affected = keys.size() - remaining;
            if (remaining == 0) {
                return DataEntryPhysicalMutationResult.succeeded(affected);
            }
            return DataEntryPhysicalMutationResult.incomplete(
                    affected, true, "DELETE_POSTCHECK_FAILED",
                    "ClickHouse 删除完成后仍存在部分业务主键，请人工核对"
            );
        } catch (RuntimeException exception) {
            String code = exception instanceof DataEntryPhysicalAccessException accessException
                    ? accessException.code() : "DELETE_POSTCHECK_FAILED";
            return DataEntryPhysicalMutationResult.incomplete(
                    0, true, code, "ClickHouse 删除已提交但无法复查结果，请人工核对"
            );
        }
    }

    @Override
    public boolean hasDuplicateBusinessKey(
            DataSource dataSource,
            DataModel model,
            List<DataModelField> businessKeyFields
    ) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        String columns = businessKeyFields.stream().map(field -> dialect.quoteIdentifier(field.getCode()))
                .collect(Collectors.joining(", "));
        String sql = "SELECT " + columns + " FROM " + dialect.qualifiedName(table(dialect, dataSource, model))
                + " GROUP BY " + columns + " HAVING COUNT(*) > 1 LIMIT 1";
        try (Connection connection = open(dialect, dataSource, QUERY_TIMEOUT_SECONDS);
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    @Override
    public List<DataEntryBusinessKeyMatch> findBusinessKeyMatches(
            DataSource dataSource,
            DataModel model,
            List<DataModelField> businessKeyFields,
            List<Map<String, Object>> keys
    ) {
        if (keys.isEmpty()) return List.of();
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        List<DataEntryBusinessKeyMatch> matches = new ArrayList<>();
        try (Connection connection = open(dialect, dataSource, QUERY_TIMEOUT_SECONDS)) {
            for (int from = 0; from < keys.size(); from += KEY_QUERY_BATCH_SIZE) {
                List<Map<String, Object>> batch = keys.subList(from, Math.min(from + KEY_QUERY_BATCH_SIZE, keys.size()));
                matches.addAll(findBusinessKeyMatches(
                        connection, dialect, dataSource, model, businessKeyFields, batch
                ));
            }
            return List.copyOf(matches);
        } catch (DataEntryPhysicalAccessException exception) {
            throw exception;
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    private List<DataEntryBusinessKeyMatch> findBusinessKeyMatches(
            Connection connection,
            DatabaseDialect dialect,
            DataSource dataSource,
            DataModel model,
            List<DataModelField> businessKeyFields,
            List<Map<String, Object>> keys
    ) throws SQLException {
        String columns = businessKeyFields.stream().map(field -> dialect.quoteIdentifier(field.getCode()))
                .collect(Collectors.joining(", "));
        String sql = "SELECT " + columns + ", COUNT(*) FROM "
                + dialect.qualifiedName(table(dialect, dataSource, model))
                + " WHERE " + keyPredicate(dialect, businessKeyFields, keys.size())
                + " GROUP BY " + columns;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            JdbcPlatformParameterBinder.bind(statement, keyParameters(businessKeyFields, keys));
            try (ResultSet resultSet = statement.executeQuery()) {
                List<DataEntryBusinessKeyMatch> matches = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> key = new LinkedHashMap<>();
                    for (int index = 0; index < businessKeyFields.size(); index++) {
                        DataModelField field = businessKeyFields.get(index);
                        key.put(field.getCode(), readPlatformValue(resultSet, index + 1, field));
                    }
                    matches.add(new DataEntryBusinessKeyMatch(key, resultSet.getLong(businessKeyFields.size() + 1)));
                }
                return matches;
            }
        }
    }

    @Override
    public List<Map<String, Object>> queryLookupOptions(
            DataSource dataSource,
            DataModel model,
            DataModelField valueField,
            DataModelField labelField,
            String keyword,
            int pageNo,
            int pageSize,
            List<Object> values
    ) {
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        String valueColumn = dialect.quoteIdentifier(valueField.getCode());
        String labelColumn = dialect.quoteIdentifier(labelField.getCode());
        StringBuilder sql = new StringBuilder("SELECT ").append(valueColumn).append(", ").append(labelColumn)
                .append(" FROM ").append(dialect.qualifiedName(table(dialect, dataSource, model)));
        List<SqlQueryParameter> parameters = new ArrayList<>();
        if (!values.isEmpty()) {
            sql.append(" WHERE ").append(valueColumn).append(" IN (")
                    .append(values.stream().map(ignored -> "?").collect(Collectors.joining(", ")))
                    .append(")");
            values.forEach(value -> parameters.add(new SqlQueryParameter(value, type(valueField))));
        } else if (keyword != null && !keyword.isBlank()) {
            sql.append(" WHERE LOWER(").append(labelColumn).append(") LIKE ?");
            parameters.add(new SqlQueryParameter("%" + keyword.trim().toLowerCase(java.util.Locale.ROOT) + "%", type(labelField)));
        }
        sql.append(" ORDER BY ").append(labelColumn).append(" ASC, ").append(valueColumn).append(" ASC")
                .append(" LIMIT ? OFFSET ?");
        parameters.add(new SqlQueryParameter(pageSize + 1, PlatformTypeDefinition.of(PlatformDataType.INTEGER)));
        parameters.add(new SqlQueryParameter((pageNo - 1) * pageSize, PlatformTypeDefinition.of(PlatformDataType.INTEGER)));

        try (Connection connection = open(dialect, dataSource, QUERY_TIMEOUT_SECONDS);
             PreparedStatement statement = connection.prepareStatement(sql.toString())) {
            statement.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
            JdbcPlatformParameterBinder.bind(statement, parameters);
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Map<String, Object>> result = new ArrayList<>();
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("value", readPlatformValue(resultSet, 1, valueField));
                    row.put("label", readPlatformValue(resultSet, 2, labelField));
                    result.add(Collections.unmodifiableMap(row));
                }
                return List.copyOf(result);
            }
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    private static String insertSql(
            DatabaseDialect dialect,
            DataSource dataSource,
            DataModel model,
            List<DataModelField> fields
    ) {
        String columns = fields.stream().map(field -> dialect.quoteIdentifier(field.getCode()))
                .collect(Collectors.joining(", "));
        String placeholders = fields.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        return "INSERT INTO " + dialect.qualifiedName(table(dialect, dataSource, model))
                + " (" + columns + ") VALUES (" + placeholders + ")";
    }

    private static String keyPredicate(
            DatabaseDialect dialect,
            List<DataModelField> businessKeyFields,
            int keyCount
    ) {
        String oneKey = businessKeyFields.stream()
                .map(field -> dialect.quoteIdentifier(field.getCode()) + " = ?")
                .collect(Collectors.joining(" AND ", "(", ")"));
        return java.util.stream.IntStream.range(0, keyCount)
                .mapToObj(ignored -> oneKey)
                .collect(Collectors.joining(" OR "));
    }

    private static List<SqlQueryParameter> keyParameters(
            List<DataModelField> businessKeyFields,
            List<Map<String, Object>> keys
    ) {
        List<SqlQueryParameter> parameters = new ArrayList<>(businessKeyFields.size() * keys.size());
        for (Map<String, Object> key : keys) {
            for (DataModelField field : businessKeyFields) {
                parameters.add(new SqlQueryParameter(key.get(field.getCode()), type(field)));
            }
        }
        return parameters;
    }

    private static List<String> canonicalKey(Map<String, Object> values, List<DataModelField> fields) {
        return fields.stream().map(field -> DataEntryValueCanonicalizer.canonical(values.get(field.getCode()), field))
                .toList();
    }

    private Connection open(DatabaseDialect dialect, DataSource dataSource, int timeoutSeconds) {
        try {
            JdbcConnectionSpec spec =
                    dialect.createConnectionSpec(dataSource.getConnection().toJdbcConnectionConfig());
            if (dataSource.getType() == DataSourceType.CLICKHOUSE) {
                Properties properties = new Properties();
                properties.putAll(spec.properties());
                properties.setProperty("socket_timeout", String.valueOf(timeoutSeconds * 1000));
                spec = new JdbcConnectionSpec(
                        spec.driverClassName(), spec.jdbcUrl(), properties, spec.schemaName()
                );
            }
            return connectionFactory.open(spec);
        } catch (ClassNotFoundException | LinkageError exception) {
            throw new DataEntryPhysicalAccessException("DRIVER_NOT_AVAILABLE", "数据库驱动未安装", exception);
        } catch (SQLException exception) {
            throw translate(exception);
        }
    }

    private static Object readPlatformValue(
            ResultSet resultSet,
            int index,
            DataModelField field
    ) throws SQLException {
        Object raw = resultSet.getObject(index);
        Object compatible = raw;
        if (field.getFieldType() == PlatformDataType.DATE && raw instanceof java.sql.Date date) {
            compatible = date.toLocalDate();
        } else if (field.getFieldType() == PlatformDataType.TIMESTAMP_NTZ && raw instanceof Timestamp timestamp) {
            compatible = timestamp.toLocalDateTime();
        } else if (field.getFieldType() == PlatformDataType.TIMESTAMP && raw instanceof Timestamp timestamp) {
            compatible = timestamp.toInstant();
        } else if (field.getFieldType() == PlatformDataType.TIMESTAMP && raw instanceof ZonedDateTime dateTime) {
            compatible = dateTime.toOffsetDateTime();
        } else if (field.getFieldType() == PlatformDataType.TIMESTAMP && raw instanceof LocalDateTime dateTime) {
            // Managed ClickHouse TIMESTAMP columns use DateTime64 in UTC; some drivers expose a local value.
            compatible = dateTime.toInstant(ZoneOffset.UTC);
        }
        return PlatformQueryValueConverter.convert(compatible, type(field));
    }

    private static TableIdentifier table(DatabaseDialect dialect, DataSource dataSource, DataModel model) {
        JdbcConnectionConfig config = dataSource.getConnection().toJdbcConnectionConfig();
        return new TableIdentifier(
                dialect.resolveCatalog(config, model.getCatalogName()),
                dialect.resolveSchema(config, model.getSchemaName()),
                model.getPhysicalTableName()
        );
    }

    static PlatformTypeDefinition type(DataModelField field) {
        return switch (field.getFieldType()) {
            case STRING -> PlatformTypeDefinition.string(field.getLength());
            case DECIMAL -> PlatformTypeDefinition.decimal(field.getPrecision(), field.getScale());
            case GEOMETRY -> PlatformTypeDefinition.geometry(field.getGeometry());
            default -> PlatformTypeDefinition.of(field.getFieldType());
        };
    }

    private static DataEntryPhysicalMutationResult incompleteBatch(
            int previouslyAffected,
            SQLException exception,
            boolean acceptNonNegativeUpdateCount
    ) {
        int currentAffected = exception instanceof BatchUpdateException batch
                ? confirmedAffected(batch.getUpdateCounts(), acceptNonNegativeUpdateCount)
                : 0;
        int affected = previouslyAffected + currentAffected;
        boolean verificationRequired = !(exception instanceof BatchUpdateException) || resultUnknown(exception);
        return DataEntryPhysicalMutationResult.incomplete(
                affected,
                verificationRequired,
                errorCode(exception),
                verificationRequired
                        ? "批量导入结果无法完全确认，请人工核对目标表，系统不会自动重试"
                        : "批量导入部分失败，已成功批次不会回滚"
        );
    }

    private static int confirmedAffected(int[] results, boolean acceptNonNegativeUpdateCount) {
        return confirmedIndexes(results, acceptNonNegativeUpdateCount).size();
    }

    private static List<Integer> confirmedIndexes(int[] results, boolean acceptNonNegativeUpdateCount) {
        List<Integer> indexes = new ArrayList<>();
        for (int index = 0; index < results.length; index++) {
            int result = results[index];
            if (result == Statement.SUCCESS_NO_INFO || result > 0
                    || (acceptNonNegativeUpdateCount && result >= 0)) indexes.add(index);
        }
        return List.copyOf(indexes);
    }

    private static boolean resultUnknown(SQLException exception) {
        if (exception instanceof SQLTimeoutException) return true;
        String state = exception.getSQLState();
        return state == null || state.startsWith("08");
    }

    private static String errorCode(SQLException exception) {
        if (exception instanceof SQLTimeoutException) return "QUERY_TIMEOUT";
        String state = exception.getSQLState();
        if (state != null && state.startsWith("23")) return "DATA_CONFLICT";
        if (state != null && state.startsWith("08")) return "NETWORK_ERROR";
        if (state != null && state.startsWith("28")) return "AUTHENTICATION_FAILED";
        return "DATABASE_ERROR";
    }

    private static DataEntryPhysicalAccessException translate(SQLException exception) {
        String code = errorCode(exception);
        String message = switch (code) {
            case "QUERY_TIMEOUT" -> "目标数据库操作超时";
            case "DATA_CONFLICT" -> "数据约束冲突，可能存在重复业务主键或非法引用";
            case "NETWORK_ERROR" -> "目标数据库连接失败";
            case "AUTHENTICATION_FAILED" -> "目标数据库认证失败";
            default -> "目标数据库操作失败";
        };
        return new DataEntryPhysicalAccessException(code, message, exception);
    }

    private static final class ImportBatchState {
        private final PreparedStatement statement;
        private final List<DataModelField> fields;
        private final boolean acceptNonNegativeUpdateCount;
        private final DataEntryImportBatchListener listener;
        private final List<Map<String, Object>> pendingRows = new ArrayList<>();
        private int pendingCount;
        private int affectedCount;

        private ImportBatchState(
                PreparedStatement statement,
                List<DataModelField> fields,
                boolean acceptNonNegativeUpdateCount,
                DataEntryImportBatchListener listener
        ) {
            this.statement = statement;
            this.fields = fields;
            this.acceptNonNegativeUpdateCount = acceptNonNegativeUpdateCount;
            this.listener = listener;
        }

        private void add(Map<String, Object> row) {
            try {
                JdbcPlatformParameterBinder.bind(statement, fields.stream()
                        .map(field -> new SqlQueryParameter(row.get(field.getCode()), type(field))).toList());
                statement.addBatch();
                pendingRows.add(Collections.unmodifiableMap(new LinkedHashMap<>(row)));
                pendingCount++;
                if (pendingCount >= IMPORT_BATCH_SIZE) flush();
            } catch (SQLException exception) {
                throw new ImportBatchException(exception);
            }
        }

        private void flush() {
            if (pendingCount == 0) return;
            List<Map<String, Object>> batch = List.copyOf(pendingRows);
            try {
                if (listener != null) listener.beforeBatch(batch);
                int[] results = statement.executeBatch();
                if (results.length != pendingCount) {
                    throw new BatchUpdateException("Batch result count mismatch", results);
                }
                for (int result : results) {
                    if (result != 1 && result != Statement.SUCCESS_NO_INFO
                            && !(acceptNonNegativeUpdateCount && result >= 0)) {
                        throw new BatchUpdateException("Unexpected batch update count", results);
                    }
                }
                statement.clearBatch();
                affectedCount += pendingCount;
                pendingCount = 0;
                pendingRows.clear();
                if (listener != null) {
                    try {
                        listener.afterBatch(batch,
                                java.util.stream.IntStream.range(0, batch.size()).boxed().toList(),
                                false, null, null);
                    }
                    catch (RuntimeException exception) { throw new HistoryCallbackException(exception); }
                }
            } catch (SQLException exception) {
                List<Integer> confirmed = exception instanceof BatchUpdateException batchException
                        ? confirmedIndexes(batchException.getUpdateCounts(), acceptNonNegativeUpdateCount) : List.of();
                if (listener != null) {
                    try {
                        listener.afterBatch(batch, confirmed, resultUnknown(exception),
                                errorCode(exception), exception.getMessage());
                    } catch (RuntimeException callbackException) {
                        affectedCount += confirmed.size();
                        throw new HistoryCallbackException(callbackException);
                    }
                }
                throw new ImportBatchException(exception);
            }
        }
    }

    private static final class HistoryCallbackException extends RuntimeException {
        private HistoryCallbackException(RuntimeException cause) { super(cause); }
    }

    private static final class ImportBatchException extends RuntimeException {
        private ImportBatchException(SQLException cause) {
            super(cause);
        }

        @Override
        public synchronized SQLException getCause() {
            return (SQLException) super.getCause();
        }
    }
}

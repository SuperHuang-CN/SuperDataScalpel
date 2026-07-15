package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.query.CompiledStandardQuery;
import cn.superhuang.data.scalpel.dialect.query.PreparedQuery;
import cn.superhuang.data.scalpel.dialect.query.QueryParameter;
import cn.superhuang.data.scalpel.dialect.query.QueryValueType;
import cn.superhuang.data.scalpel.dialect.query.StandardQueryResult;

import java.math.BigDecimal;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JDBC-only executor for compiled standard queries. It never accepts SQL text from callers. */
public final class JdbcStandardQueryExecutor {

    public StandardQueryResult execute(
            Connection connection,
            CompiledStandardQuery query,
            int maximumRows,
            Duration timeout
    ) throws SQLException {
        if (maximumRows < 1) {
            throw new IllegalArgumentException("maximumRows must be positive");
        }
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        Long totalCount = query.countQuery() == null ? null : executeCount(connection, query.countQuery(), timeout);
        List<Map<String, Object>> rows = executeRows(connection, query.dataQuery(), maximumRows, timeout);
        return new StandardQueryResult(totalCount, rows);
    }

    private Long executeCount(Connection connection, PreparedQuery query, Duration timeout) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
            configure(statement, 1, timeout);
            bind(statement, query.parameters());
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return 0L;
                }
                return resultSet.getLong(1);
            }
        }
    }

    private List<Map<String, Object>> executeRows(
            Connection connection,
            PreparedQuery query,
            int maximumRows,
            Duration timeout
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
            configure(statement, maximumRows, timeout);
            bind(statement, query.parameters());
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                List<String> labels = new ArrayList<>(metadata.getColumnCount());
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    labels.add(metadata.getColumnLabel(index));
                }
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next() && rows.size() < maximumRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int index = 1; index <= labels.size(); index++) {
                        row.put(labels.get(index - 1), normalize(resultSet.getObject(index)));
                    }
                    rows.add(Collections.unmodifiableMap(row));
                }
                return List.copyOf(rows);
            }
        }
    }

    private static void configure(PreparedStatement statement, int maximumRows, Duration timeout) throws SQLException {
        statement.setMaxRows(maximumRows);
        try {
            statement.setQueryTimeout(Math.max(1, Math.toIntExact(timeout.toSeconds())));
        } catch (SQLException ignored) {
            // Not every JDBC driver supports statement-level timeouts.
        }
    }

    private static void bind(PreparedStatement statement, List<QueryParameter> parameters) throws SQLException {
        for (int index = 0; index < parameters.size(); index++) {
            QueryParameter parameter = parameters.get(index);
            bind(statement, index + 1, parameter);
        }
    }

    private static void bind(PreparedStatement statement, int index, QueryParameter parameter) throws SQLException {
        if (parameter.value() == null) {
            statement.setObject(index, null);
            return;
        }
        switch (parameter.type()) {
            case STRING -> statement.setString(index, String.valueOf(parameter.value()));
            case INTEGER -> statement.setInt(index, number(parameter.value()).intValue());
            case LONG -> statement.setLong(index, number(parameter.value()).longValue());
            case DECIMAL -> statement.setBigDecimal(index, decimal(parameter.value()));
            case BOOLEAN -> statement.setBoolean(index, booleanValue(parameter.value()));
            case DATE -> statement.setObject(index, localDate(parameter.value()));
            case DATETIME -> statement.setObject(index, localDateTime(parameter.value()));
        }
    }

    private static Number number(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected a numeric query value", exception);
        }
    }

    private static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Expected a decimal query value", exception);
        }
    }

    private static boolean booleanValue(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if ("true".equalsIgnoreCase(String.valueOf(value))) {
            return true;
        }
        if ("false".equalsIgnoreCase(String.valueOf(value))) {
            return false;
        }
        throw new IllegalArgumentException("Expected a boolean query value");
    }

    private static LocalDate localDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static LocalDateTime localDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return LocalDateTime.parse(String.valueOf(value));
    }

    private static Object normalize(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal.toPlainString();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().toString();
        }
        if (value instanceof LocalDate || value instanceof LocalDateTime) {
            return value.toString();
        }
        if (value instanceof Clob clob) {
            return clob.getSubString(1, Math.toIntExact(clob.length()));
        }
        if (value instanceof byte[]) {
            throw new SQLException("BINARY fields cannot be returned by a standard service");
        }
        return value;
    }
}

package cn.superhuang.data.scalpel.dialect.runtime;

import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.query.CompiledSqlServiceQuery;
import cn.superhuang.data.scalpel.dialect.query.PreparedSqlQuery;
import cn.superhuang.data.scalpel.dialect.query.QueryInspection;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryResult;

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
import java.time.LocalTime;
import java.time.OffsetTime;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes only dialect-compiled, parameterized SQL service queries. */
public final class JdbcSqlQueryExecutor {

    public SqlQueryResult execute(
            Connection connection,
            DatabaseDialect dialect,
            CompiledSqlServiceQuery query,
            int maximumRows,
            Duration timeout
    ) throws SQLException {
        if (maximumRows < 1 || timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Invalid SQL query execution limits");
        }
        Long count = query.countQuery() == null ? null : count(connection, query.countQuery(), timeout);
        return rows(connection, dialect, query.dataQuery(), maximumRows, timeout, count);
    }

    private static long count(Connection connection, PreparedSqlQuery query, Duration timeout) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
            configure(statement, 1, timeout);
            JdbcPlatformParameterBinder.bind(statement, query.parameters());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getLong(1) : 0L;
            }
        }
    }

    private static SqlQueryResult rows(
            Connection connection,
            DatabaseDialect dialect,
            PreparedSqlQuery query,
            int maximumRows,
            Duration timeout,
            Long count
    ) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
            configure(statement, maximumRows, timeout);
            JdbcPlatformParameterBinder.bind(statement, query.parameters());
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                QueryInspection inspection = JdbcQueryMetadata.inspect(metadata, dialect);
                List<String> labels = inspection.columns().stream().map(column -> column.label()).toList();
                List<Map<String, Object>> rows = new ArrayList<>();
                while (resultSet.next() && rows.size() < maximumRows) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int index = 1; index <= labels.size(); index++) {
                        row.put(labels.get(index - 1), normalize(resultSet.getObject(index)));
                    }
                    rows.add(Collections.unmodifiableMap(row));
                }
                return new SqlQueryResult(count, rows, inspection);
            }
        }
    }

    private static void configure(PreparedStatement statement, int maximumRows, Duration timeout) throws SQLException {
        statement.setMaxRows(maximumRows);
        try {
            statement.setQueryTimeout(Math.max(1, Math.toIntExact(timeout.toSeconds())));
        } catch (SQLException ignored) {
            // Some drivers do not expose statement-level timeouts.
        }
    }

    private static Object normalize(Object value) throws SQLException {
        if (value == null) return null;
        if (value instanceof BigDecimal decimal) return decimal.toPlainString();
        if (value instanceof java.sql.Date date) return date.toLocalDate().toString();
        if (value instanceof java.sql.Time time) return time.toLocalTime().toString();
        if (value instanceof Timestamp timestamp) return timestamp.toLocalDateTime().toString();
        if (value instanceof LocalDate || value instanceof LocalTime || value instanceof LocalDateTime
                || value instanceof OffsetTime || value instanceof OffsetDateTime || value instanceof Instant) {
            return value.toString();
        }
        if (value instanceof Clob clob) return clob.getSubString(1, Math.toIntExact(clob.length()));
        if (value instanceof byte[]) throw new SQLException("BINARY fields cannot be returned by a SQL service");
        return value;
    }
}

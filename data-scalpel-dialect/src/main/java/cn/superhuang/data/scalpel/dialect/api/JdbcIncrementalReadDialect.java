package cn.superhuang.data.scalpel.dialect.api;

import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

/** Optional dialect contract for controlled time-window JDBC streaming reads. */
public interface JdbcIncrementalReadDialect {

    default Instant readDatabaseCurrentTime(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT CURRENT_TIMESTAMP");
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next() || resultSet.getTimestamp(1) == null) {
                throw new SQLException("Database current time query returned no value");
            }
            return resultSet.getTimestamp(1).toInstant();
        }
    }

    default String renderIncrementalWindowQuery(
            DatabaseDialect dialect,
            TableIdentifier table,
            List<String> projectedColumns,
            String timeColumn,
            boolean lowerBounded
    ) {
        if (dialect == null || table == null || projectedColumns == null || projectedColumns.isEmpty()
                || timeColumn == null || timeColumn.isBlank()) {
            throw new IllegalArgumentException("Invalid incremental window query arguments");
        }
        String projection = projectedColumns.stream()
                .map(dialect::quoteIdentifier)
                .collect(Collectors.joining(", "));
        String cursor = dialect.quoteIdentifier(timeColumn);
        return "SELECT " + projection + " FROM " + dialect.qualifiedName(table)
                + " WHERE " + (lowerBounded ? cursor + " > ? AND " : "") + cursor + " <= ?";
    }

    default void bindTime(
            PreparedStatement statement,
            int parameterIndex,
            Instant offset,
            PlatformDataType temporalType,
            ZoneId cursorTimeZone
    ) throws SQLException {
        if (offset == null || temporalType == null || cursorTimeZone == null) {
            throw new IllegalArgumentException("Incremental time binding arguments are required");
        }
        if (temporalType == PlatformDataType.TIMESTAMP) {
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            statement.setTimestamp(parameterIndex, Timestamp.from(offset), utc);
            return;
        }
        if (temporalType == PlatformDataType.TIMESTAMP_NTZ) {
            LocalDateTime local = LocalDateTime.ofInstant(offset, cursorTimeZone);
            statement.setObject(parameterIndex, local, Types.TIMESTAMP);
            return;
        }
        throw new IllegalArgumentException("Incremental cursor must be TIMESTAMP or TIMESTAMP_NTZ");
    }
}

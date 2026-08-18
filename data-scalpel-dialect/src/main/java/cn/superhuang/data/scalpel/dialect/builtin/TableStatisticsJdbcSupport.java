package cn.superhuang.data.scalpel.dialect.builtin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

final class TableStatisticsJdbcSupport {

    private TableStatisticsJdbcSupport() {
    }

    static int timeoutSeconds(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return 10;
        }
        return Math.max(1, Math.toIntExact(timeout.toSeconds()));
    }

    static long deadlineNanos(Duration timeout) {
        return System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds(timeout));
    }

    static int remainingTimeoutSeconds(long deadlineNanos) throws SQLTimeoutException {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0) {
            throw new SQLTimeoutException("Physical statistics query timed out");
        }
        long seconds = Math.max(1L, TimeUnit.NANOSECONDS.toSeconds(remainingNanos));
        return Math.toIntExact(Math.min(Integer.MAX_VALUE, seconds));
    }

    static Long nullableLong(ResultSet resultSet, int index) throws SQLException {
        Object value = resultSet.getObject(index);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new SQLException("Physical statistics value is not numeric: " + value.getClass().getName());
    }
}

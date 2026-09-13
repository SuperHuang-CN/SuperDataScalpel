package cn.superhuang.datascalpel.taskengine.runner;

import org.apache.spark.sql.jdbc.JdbcDialects;
import org.apache.spark.sql.jdbc.PostgresDialect;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** Applies Spark's PostgreSQL JDBC behavior to PostgreSQL-compatible vendor URL schemes. */
final class PostgreSqlFamilySparkJdbcDialect extends PostgresDialect {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    @Override
    public boolean canHandle(String url) {
        if (url == null) {
            return false;
        }
        String normalized = url.toLowerCase(Locale.ROOT);
        return normalized.startsWith("jdbc:highgo:")
                || normalized.startsWith("jdbc:opengauss:")
                || normalized.startsWith("jdbc:kingbase8:");
    }

    static void ensureRegistered() {
        if (REGISTERED.compareAndSet(false, true)) {
            JdbcDialects.registerDialect(new PostgreSqlFamilySparkJdbcDialect());
        }
    }
}

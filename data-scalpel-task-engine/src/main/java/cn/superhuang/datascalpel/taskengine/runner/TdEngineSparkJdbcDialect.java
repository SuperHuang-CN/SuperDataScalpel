package cn.superhuang.datascalpel.taskengine.runner;

import org.apache.spark.sql.jdbc.JdbcDialect;
import org.apache.spark.sql.jdbc.JdbcDialects;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/** TDengine treats double-quoted expressions as string literals, not column identifiers. */
final class TdEngineSparkJdbcDialect extends JdbcDialect {
    private static final AtomicBoolean REGISTERED = new AtomicBoolean();

    @Override
    public boolean canHandle(String url) {
        if (url == null) return false;
        String normalized = url.toLowerCase(Locale.ROOT);
        return normalized.startsWith("jdbc:taos-ws:") || normalized.startsWith("jdbc:taos-rs:");
    }

    @Override
    public String quoteIdentifier(String column) {
        return "`" + column.replace("`", "``") + "`";
    }

    static void ensureRegistered() {
        if (REGISTERED.compareAndSet(false, true)) {
            JdbcDialects.registerDialect(new TdEngineSparkJdbcDialect());
        }
    }
}

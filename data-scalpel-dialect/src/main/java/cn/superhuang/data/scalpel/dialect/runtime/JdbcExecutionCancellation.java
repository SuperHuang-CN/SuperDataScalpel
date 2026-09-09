package cn.superhuang.data.scalpel.dialect.runtime;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicBoolean;

/** One execution only; cancellation never operates on a shared or future connection. */
public final class JdbcExecutionCancellation {
    private final AtomicBoolean requested = new AtomicBoolean();
    private volatile Connection connection;
    private volatile Statement statement;

    public boolean isRequested() { return requested.get(); }
    public void check() {
        if (isRequested()) throw new DatabaseAccessException("QUERY_CANCELLED", "SQL 执行已取消", null);
    }
    public void cancel() {
        if (!requested.compareAndSet(false, true)) return;
        interruptStatement(statement);
        abort(connection);
    }
    void register(Connection value) {
        connection = value;
        if (isRequested()) abort(value);
        check();
    }
    void register(Statement value) {
        statement = value;
        if (isRequested()) interruptStatement(value);
        check();
    }
    void clearStatement() { statement = null; }
    void clear() { statement = null; connection = null; }
    private static void interruptStatement(Statement value) {
        if (value == null) return;
        Thread.startVirtualThread(() -> {
            try { value.cancel(); } catch (SQLException | RuntimeException ignored) { /* Connection abort also requested. */ }
        });
    }
    private static void abort(Connection value) {
        if (value == null) return;
        // Run independently: some JDBC drivers block inside Statement.cancel().
        Thread.startVirtualThread(() -> {
            try { value.abort(Runnable::run); }
            catch (SQLException | RuntimeException | AbstractMethodError exception) {
                try { value.close(); } catch (SQLException ignored) { /* Worker keeps the run active until it exits. */ }
            }
        });
    }
}

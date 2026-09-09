package cn.superhuang.data.scalpel.dialect.runtime;

import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.SQLFeatureNotSupportedException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class JdbcExecutionCancellationTest {
    @Test void abortDoesNotWaitForABlockedStatementCancel() throws Exception {
        var statementCancelEntered = new CountDownLatch(1);
        var releaseStatementCancel = new CountDownLatch(1);
        var aborted = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var statement = proxy(Statement.class, (method) -> {
            if (method.equals("cancel")) { calls.incrementAndGet(); statementCancelEntered.countDown(); releaseStatementCancel.await(); }
        });
        var connection = proxy(Connection.class, method -> { if (method.equals("abort")) aborted.countDown(); });
        var cancellation = new JdbcExecutionCancellation(); cancellation.register(connection); cancellation.register(statement);
        try {
            cancellation.cancel(); cancellation.cancel();
            assertTrue(statementCancelEntered.await(5, TimeUnit.SECONDS));
            assertTrue(aborted.await(5, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            assertEquals("QUERY_CANCELLED", assertThrows(DatabaseAccessException.class, cancellation::check).code());
        } finally { releaseStatementCancel.countDown(); }
    }
    @Test void cancellationBeforeConnectionRegistrationStillAbortsAndFallsBackToClose() throws Exception {
        var closed = new CountDownLatch(1);
        var connection = proxy(Connection.class, method -> {
            if (method.equals("abort")) throw new SQLFeatureNotSupportedException();
            if (method.equals("close")) closed.countDown();
        });
        var cancellation = new JdbcExecutionCancellation(); cancellation.cancel();
        assertThrows(DatabaseAccessException.class, () -> cancellation.register(connection));
        assertTrue(closed.await(5, TimeUnit.SECONDS));
    }
    @Test void completedExecutionDoesNotCancelADetachedConnection() {
        var calls = new AtomicInteger();
        var connection = proxy(Connection.class, method -> calls.incrementAndGet());
        var cancellation = new JdbcExecutionCancellation(); cancellation.register(connection); cancellation.clear();
        cancellation.cancel();
        assertEquals(0, calls.get());
    }
    private interface Call { void invoke(String method) throws Exception; }
    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            call.invoke(method.getName()); return null;
        }));
    }
}

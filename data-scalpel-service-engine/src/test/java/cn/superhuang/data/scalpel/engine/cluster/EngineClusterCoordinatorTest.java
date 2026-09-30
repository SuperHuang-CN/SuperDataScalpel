package cn.superhuang.data.scalpel.engine.cluster;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class EngineClusterCoordinatorTest {
    @Test
    void readinessIsMemoryOnlyAndExpiresWithoutBackgroundProgress() throws Exception {
        var fixture = new Fixture();
        try {
            assertFalse(fixture.coordinator.ready());
            fixture.coordinator.start();
            assertTrue(fixture.coordinator.ready());
            clearInvocations(fixture.clock, fixture.source);
            for (int i = 0; i < 1000; i++) assertTrue(fixture.coordinator.ready());
            verifyNoInteractions(fixture.clock, fixture.source);
            ReflectionTestUtils.setField(fixture.coordinator, "confirmedNanos",
                    System.nanoTime() - TimeUnit.SECONDS.toNanos(80));
            assertFalse(fixture.coordinator.ready());
        } finally {
            fixture.coordinator.stop();
        }
        assertFalse(fixture.coordinator.ready());
    }

    @Test
    void failedSnapshotNeverBecomesReadyAndCommandInvalidatesEvenOnFailure() throws Exception {
        var fixture = new Fixture();
        try {
            doThrow(new IllegalStateException("snapshot unavailable")).when(fixture.reconciler).reconcile();
            fixture.coordinator.start();
            assertFalse(fixture.coordinator.ready());
            assertNotNull(fixture.coordinator.snapshot().error());
            doNothing().when(fixture.reconciler).reconcile();
            assertThrows(IllegalArgumentException.class, () -> fixture.coordinator.command(() -> {
                throw new IllegalArgumentException("operation failed");
            }));
            verify(fixture.clock, times(2)).changed();
            assertTrue(fixture.coordinator.ready());
        } finally {
            fixture.coordinator.stop();
        }
    }

    private static class Fixture {
        final DataSource source = mock(DataSource.class);
        final EngineClusterClock clock = mock(EngineClusterClock.class);
        final EngineRuntimeReconciler reconciler = mock(EngineRuntimeReconciler.class);
        final EngineClusterCoordinator coordinator;
        @SuppressWarnings("unchecked")
        Fixture() throws Exception {
            var connection = mock(Connection.class);
            var metadata = mock(DatabaseMetaData.class);
            var statement = mock(PreparedStatement.class);
            var result = mock(ResultSet.class);
            when(source.getConnection()).thenReturn(connection);
            when(connection.getMetaData()).thenReturn(metadata);
            when(metadata.getDatabaseProductName()).thenReturn("PostgreSQL");
            when(connection.prepareStatement(anyString())).thenReturn(statement);
            when(statement.executeQuery()).thenReturn(result);
            when(result.getBoolean(1)).thenReturn(true);
            ObjectProvider<EngineRuntimeReconciler> provider = mock(ObjectProvider.class);
            when(provider.getObject()).thenReturn(reconciler);
            coordinator = new EngineClusterCoordinator(source, clock,
                    new EngineClusterProperties(true, 30000, 70000), provider);
        }
    }
}

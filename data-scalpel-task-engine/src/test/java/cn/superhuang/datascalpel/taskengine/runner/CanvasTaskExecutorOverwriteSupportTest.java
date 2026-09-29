package cn.superhuang.datascalpel.taskengine.runner;

import cn.superhuang.data.scalpel.contract.task.DataSourcePurpose;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDataSource;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeDatabaseType;
import cn.superhuang.datascalpel.taskengine.contract.RuntimeJdbcConnection;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CanvasTaskExecutorOverwriteSupportTest {

    @Test
    void allowsBatchOverwriteForAllRegularJdbcDatabases() {
        for (RuntimeDatabaseType databaseType : List.of(
                RuntimeDatabaseType.POSTGRESQL,
                RuntimeDatabaseType.MYSQL,
                RuntimeDatabaseType.ORACLE,
                RuntimeDatabaseType.SQL_SERVER,
                RuntimeDatabaseType.CLICKHOUSE,
                RuntimeDatabaseType.DAMENG,
                RuntimeDatabaseType.OPENGAUSS,
                RuntimeDatabaseType.KINGBASE
        )) {
            assertDoesNotThrow(
                    () -> DirectJdbcWriter.requireOverwriteSupported(runtimeDataSource(databaseType)),
                    databaseType.name()
            );
        }
    }

    @Test
    void rejectsTdEngineBeforeTruncating() {
        RunnerExecutionException exception = assertThrows(
                RunnerExecutionException.class,
                () -> DirectJdbcWriter.requireOverwriteSupported(
                        runtimeDataSource(RuntimeDatabaseType.TDENGINE_WEBSOCKET)
                )
        );

        assertEquals("OVERWRITE_DATABASE_NOT_SUPPORTED", exception.code());
    }

    private static RuntimeDataSource runtimeDataSource(RuntimeDatabaseType databaseType) {
        return new RuntimeDataSource(UUID.randomUUID(), databaseType, Set.of(DataSourcePurpose.DISTRIBUTION),
                new RuntimeJdbcConnection("example.Driver", "jdbc:example:test", null, null,
                        "user", "password", Map.of()));
    }

    @Test
    void sharedWriterPreservesUpsertAndGeometryCapabilityBoundaries() {
        for (RuntimeDatabaseType type : List.of(RuntimeDatabaseType.POSTGRESQL, RuntimeDatabaseType.MYSQL,
                RuntimeDatabaseType.HIGHGO, RuntimeDatabaseType.OPENGAUSS, RuntimeDatabaseType.KINGBASE)) {
            assertDoesNotThrow(() -> DirectJdbcWriter.requireSupported(runtimeDataSource(type), "UPSERT", true, "output"));
        }
        for (RuntimeDatabaseType type : List.of(RuntimeDatabaseType.ORACLE, RuntimeDatabaseType.DAMENG,
                RuntimeDatabaseType.SQL_SERVER)) {
            assertDoesNotThrow(() -> DirectJdbcWriter.requireSupported(runtimeDataSource(type), "UPSERT", false, "output"));
            var failure = assertThrows(RunnerExecutionException.class,
                    () -> DirectJdbcWriter.requireSupported(runtimeDataSource(type), "APPEND", true, "output"));
            assertEquals("SPATIAL_JDBC_UNSUPPORTED", failure.code());
            assertEquals("output", failure.nodeId());
        }
        var failure = assertThrows(RunnerExecutionException.class, () -> DirectJdbcWriter.requireSupported(
                runtimeDataSource(RuntimeDatabaseType.CLICKHOUSE), "UPSERT", false, "output"));
        assertEquals("UPSERT_DATABASE_NOT_SUPPORTED", failure.code());
    }

}

package cn.superhuang.datascalpel.taskengine.contract;

import cn.superhuang.data.scalpel.contract.task.CanvasJdbcDatabaseType;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RuntimeDatabaseTypeCoverageTest {

    @Test
    void everyCanvasJdbcDatabaseTypeHasRunnerAndDialectMappings() {
        for (CanvasJdbcDatabaseType type : CanvasJdbcDatabaseType.values()) {
            RuntimeDatabaseType runtime = assertDoesNotThrow(
                    () -> RuntimeDatabaseType.valueOf(type.name()),
                    type.name()
            );
            assertDoesNotThrow(() -> BuiltInDialects.registry().require(runtime.name()), type.name());
        }
    }

    @Test
    void newDatabaseTypesUseExpectedJdbcUrlPrefixesAndDrivers() {
        Map<RuntimeDatabaseType, String> prefixes = Map.of(
                RuntimeDatabaseType.ORACLE, "jdbc:oracle:thin:@",
                RuntimeDatabaseType.SQL_SERVER, "jdbc:sqlserver://",
                RuntimeDatabaseType.CLICKHOUSE, "jdbc:clickhouse:",
                RuntimeDatabaseType.DAMENG, "jdbc:dm://"
        );
        Map<RuntimeDatabaseType, String> drivers = Map.of(
                RuntimeDatabaseType.ORACLE, "oracle.jdbc.OracleDriver",
                RuntimeDatabaseType.SQL_SERVER, "com.microsoft.sqlserver.jdbc.SQLServerDriver",
                RuntimeDatabaseType.CLICKHOUSE, "com.clickhouse.jdbc.ClickHouseDriver",
                RuntimeDatabaseType.DAMENG, "dm.jdbc.driver.DmDriver"
        );
        prefixes.forEach((type, prefix) -> {
            assertEquals(prefix, type.jdbcUrlPrefix());
            assertEquals(drivers.get(type), BuiltInDialects.registry().require(type.name()).driverClassName());
        });
    }
}

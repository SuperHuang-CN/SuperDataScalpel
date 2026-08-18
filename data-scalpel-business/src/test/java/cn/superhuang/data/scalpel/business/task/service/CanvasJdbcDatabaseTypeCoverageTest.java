package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.contract.task.CanvasJdbcDatabaseType;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class CanvasJdbcDatabaseTypeCoverageTest {

    @Test
    void everyJdbcDataSourceTypeHasCanvasManifestAndDialectMappings() {
        for (DataSourceType type : DataSourceType.values()) {
            if (!type.isJdbc()) continue;
            assertDoesNotThrow(() -> CanvasJdbcDatabaseType.valueOf(type.name()), type.name());
            assertDoesNotThrow(
                    () -> CanvasTaskRunManifest.RuntimeDatabaseType.valueOf(type.name()),
                    type.name()
            );
            assertDoesNotThrow(() -> BuiltInDialects.registry().require(type.name()), type.name());
        }
    }
}

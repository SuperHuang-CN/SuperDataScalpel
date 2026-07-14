package cn.superhuang.data.scalpel.dialect.api;

import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;

public interface DatabaseDialect {

    DatabaseDefinition definition();

    String driverClassName();

    JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config);

    String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog);

    String resolveSchema(JdbcConnectionConfig config, String requestedSchema);

    String validationQuery();

    String previewSql(TableIdentifier table, int rowLimit);

    LogicalType logicalType(int jdbcType, String nativeTypeName);
}

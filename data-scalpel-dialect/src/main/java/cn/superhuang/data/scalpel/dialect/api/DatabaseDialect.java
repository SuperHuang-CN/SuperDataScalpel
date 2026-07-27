package cn.superhuang.data.scalpel.dialect.api;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionSpec;
import cn.superhuang.data.scalpel.dialect.model.DdlPlan;
import cn.superhuang.data.scalpel.dialect.model.ColumnMetadata;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.PhysicalTypeDefinition;
import cn.superhuang.data.scalpel.dialect.model.TypeMappingResult;
import cn.superhuang.data.scalpel.dialect.model.TableDefinition;
import cn.superhuang.data.scalpel.dialect.model.TableChangeCheck;
import cn.superhuang.data.scalpel.dialect.model.TableChangePlan;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableStorageMetadata;
import cn.superhuang.data.scalpel.dialect.model.TableStructureComparison;
import cn.superhuang.data.scalpel.dialect.query.CompiledStandardQuery;
import cn.superhuang.data.scalpel.dialect.query.CompiledSqlServiceQuery;
import cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery;
import cn.superhuang.data.scalpel.dialect.query.StandardQuery;
import cn.superhuang.data.scalpel.dialect.query.SqlQueryParameter;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface DatabaseDialect {

    DatabaseDefinition definition();

    String driverClassName();

    JdbcConnectionSpec createConnectionSpec(JdbcConnectionConfig config);

    String resolveCatalog(JdbcConnectionConfig config, String requestedCatalog);

    String resolveSchema(JdbcConnectionConfig config, String requestedSchema);

    String validationQuery();

    String previewSql(TableIdentifier table, int rowLimit);

    CompiledStandardQuery compileStandardQuery(StandardQuery query);

    /** Wraps one already-validated SQL service query with controlled pagination and optional count. */
    CompiledSqlServiceQuery compileSqlServiceQuery(
            String jdbcSql,
            List<SqlQueryParameter> parameters,
            int offset,
            int limit,
            boolean returnCount
    );

    /** Renders a controlled insert-select statement from a query that has already passed read-only validation. */
    String renderInsertSelect(TableIdentifier target, List<String> targetColumns, InsertSelectQuery query);

    /** Renders the controlled cleanup step used by a dialect that supports transactional task overwrite. */
    String renderOverwriteCleanup(TableIdentifier target);

    LogicalType logicalType(int jdbcType, String nativeTypeName);

    /** Maps driver/JDBC metadata into the stable platform logical type system. */
    TypeMappingResult<PlatformTypeDefinition> mapToPlatformType(JdbcTypeDescriptor physicalType);

    /** Maps a stable platform logical type into this database's controlled physical type family. */
    TypeMappingResult<PhysicalTypeDefinition> mapToPhysicalType(PlatformTypeDefinition platformType);

    DdlPlan planCreateTable(TableDefinition definition);

    /**
     * Connection-aware create planning for physical types whose native representation depends on
     * runtime catalogs, for example database-local spatial reference identifiers.
     */
    default DdlPlan planCreateTable(Connection connection, TableDefinition definition) throws SQLException {
        return planCreateTable(definition);
    }

    TableStructureComparison compareTable(TableDefinition expected, TableMetadata actual);

    /**
     * Enriches JDBC columns with dialect-specific metadata in one table-scoped operation.
     * Implementations must not execute one query per column.
     */
    default List<ColumnMetadata> enrichColumnMetadata(
            Connection connection,
            TableIdentifier table,
            List<ColumnMetadata> columns
    ) throws SQLException {
        return List.copyOf(columns);
    }

    /** Reads dialect-specific physical storage attributes not exposed by JDBC metadata. */
    default TableStorageMetadata readTableStorageMetadata(Connection connection, TableIdentifier table) throws SQLException {
        return TableStorageMetadata.none();
    }

    /** Converts readable JDBC metadata into the portable structural representation of this dialect. */
    TableDefinition snapshotTableDefinition(TableMetadata actual);

    /** Classifies a requested structural change and renders only controlled execution options. */
    TableChangePlan planTableChange(TableDefinition before, TableDefinition target, TableMetadata actual);

    /**
     * Connection-aware planning hook for dialects whose DDL safety depends on target runtime settings.
     * Most dialects have no such settings and retain the metadata-only planning behavior.
     */
    default TableChangePlan planTableChange(
            Connection connection,
            TableDefinition before,
            TableDefinition target,
            TableMetadata actual
    ) throws SQLException {
        return planTableChange(before, target, actual);
    }

    /** Evaluates one dialect-defined precondition without accepting ad-hoc SQL. */
    boolean checkTableChange(Connection connection, TableIdentifier table, TableChangeCheck check) throws SQLException;
}

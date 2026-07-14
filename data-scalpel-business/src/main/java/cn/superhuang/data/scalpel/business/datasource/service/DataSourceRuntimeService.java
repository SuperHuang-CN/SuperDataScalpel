package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DatabaseType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.web.request.DataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.TestDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.ConnectionTestResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DatabaseTypeResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.NamespaceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableListResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableMetadataResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TablePreviewResponse;
import cn.superhuang.data.scalpel.dialect.api.DatabaseDialect;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.TableIdentifier;
import cn.superhuang.data.scalpel.dialect.model.TableQuery;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseAccessException;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseInspector;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Executes short-lived, read-only operations against a registered external database. */
@Service
public class DataSourceRuntimeService {

    private static final int TABLE_LIST_LIMIT = 500;

    private final DataSourceRepository repository;
    private final DialectRegistry registry;
    private final DatabaseInspector inspector;

    public DataSourceRuntimeService(
            DataSourceRepository repository,
            DialectRegistry registry,
            DatabaseInspector inspector
    ) {
        this.repository = repository;
        this.registry = registry;
        this.inspector = inspector;
    }

    public List<DatabaseTypeResponse> databaseTypes() {
        return registry.all().stream()
                .map(dialect -> DatabaseTypeResponse.from(
                        dialect.definition(),
                        inspector.isDriverAvailable(dialect.definition().id())
                ))
                .toList();
    }

    public ConnectionTestResponse test(TestDataSourceConnectionRequest request) {
        return ConnectionTestResponse.from(inspector.test(
                request.databaseType().name(),
                toConfig(request.connection())
        ));
    }

    public void validateConfiguration(DatabaseType databaseType, DataSourceConnectionRequest connection) {
        try {
            registry.require(databaseType.name()).createConnectionSpec(toConfig(connection));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    public ConnectionTestResponse test(UUID id) {
        DataSource dataSource = requireDataSource(id);
        return ConnectionTestResponse.from(inspector.test(
                dataSource.getDatabaseType().name(),
                dataSource.getConnection().toJdbcConnectionConfig()
        ));
    }

    public List<NamespaceResponse> listNamespaces(UUID id) {
        DataSource dataSource = requireDataSource(id);
        try {
            return inspector.listNamespaces(
                            dataSource.getDatabaseType().name(),
                            dataSource.getConnection().toJdbcConnectionConfig()
                    ).stream()
                    .map(NamespaceResponse::from)
                    .toList();
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
    }

    public TableListResponse listTables(
            UUID id,
            String catalog,
            String schema,
            String keyword,
            boolean includeViews
    ) {
        DataSource dataSource = requireDataSource(id);
        try {
            return TableListResponse.from(inspector.listTables(
                    dataSource.getDatabaseType().name(),
                    dataSource.getConnection().toJdbcConnectionConfig(),
                    new TableQuery(catalog, schema, keyword, includeViews, TABLE_LIST_LIMIT)
            ));
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
    }

    public TableMetadataResponse readTable(UUID id, String catalog, String schema, String table) {
        DataSource dataSource = requireDataSource(id);
        JdbcConnectionConfig config = dataSource.getConnection().toJdbcConnectionConfig();
        DatabaseDialect dialect = registry.require(dataSource.getDatabaseType().name());
        try {
            return TableMetadataResponse.from(inspector.readTable(
                    dataSource.getDatabaseType().name(),
                    config,
                    resolvedTable(dialect, config, catalog, schema, table)
            ));
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
    }

    public TablePreviewResponse preview(UUID id, String catalog, String schema, String table, int limit) {
        DataSource dataSource = requireDataSource(id);
        JdbcConnectionConfig config = dataSource.getConnection().toJdbcConnectionConfig();
        DatabaseDialect dialect = registry.require(dataSource.getDatabaseType().name());
        try {
            return TablePreviewResponse.from(inspector.preview(
                    dataSource.getDatabaseType().name(),
                    config,
                    resolvedTable(dialect, config, catalog, schema, table),
                    limit
            ));
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
    }

    private DataSource requireDataSource(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
    }

    private static JdbcConnectionConfig toConfig(DataSourceConnectionRequest connection) {
        return new JdbcConnectionConfig(
                connection.host(),
                connection.port(),
                connection.databaseName(),
                connection.schemaName(),
                connection.username(),
                connection.password(),
                connection.options()
        );
    }

    private static TableIdentifier resolvedTable(
            DatabaseDialect dialect,
            JdbcConnectionConfig config,
            String catalog,
            String schema,
            String table
    ) {
        return new TableIdentifier(
                dialect.resolveCatalog(config, catalog),
                dialect.resolveSchema(config, schema),
                table
        );
    }

    private static ResponseStatusException remoteAccessException(DatabaseAccessException exception) {
        HttpStatus status = "TABLE_NOT_FOUND".equals(exception.code()) ? HttpStatus.NOT_FOUND : HttpStatus.BAD_GATEWAY;
        return new ResponseStatusException(status, exception.getMessage(), exception);
    }
}

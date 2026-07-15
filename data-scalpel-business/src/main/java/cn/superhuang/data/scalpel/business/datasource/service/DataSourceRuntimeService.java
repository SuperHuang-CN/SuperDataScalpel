package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.web.request.DataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.JdbcDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.TestDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.ConnectionTestResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceTypeResponse;
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

    public List<DataSourceTypeResponse> dataSourceTypes() {
        List<DataSourceTypeResponse> result = new java.util.ArrayList<>(registry.all().stream()
                .map(dialect -> DataSourceTypeResponse.jdbc(
                        dialect.definition(), inspector.isDriverAvailable(dialect.definition().id())
                ))
                .toList());
        result.add(DataSourceTypeResponse.kafka());
        result.add(DataSourceTypeResponse.s3());
        return List.copyOf(result);
    }

    public ConnectionTestResponse test(TestDataSourceConnectionRequest request) {
        JdbcDataSourceConnectionRequest connection = requireJdbc(request.type(), request.connection());
        return ConnectionTestResponse.from(inspector.test(
                request.type().name(),
                toConfig(connection)
        ));
    }

    public void validateConfiguration(DataSourceType type, DataSourceConnectionRequest connection) {
        if (type.connectionKind() != connection.kind()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据源类型与连接配置不匹配");
        }
        if (type.connectionKind() != DataSourceConnectionKind.JDBC) {
            return;
        }
        try {
            registry.require(type.name()).createConnectionSpec(toConfig((JdbcDataSourceConnectionRequest) connection));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    public ConnectionTestResponse test(UUID id) {
        DataSource dataSource = requireDataSource(id);
        requireJdbc(dataSource);
        return ConnectionTestResponse.from(inspector.test(
                dataSource.getType().name(),
                dataSource.getConnection().toJdbcConnectionConfig()
        ));
    }

    public List<NamespaceResponse> listNamespaces(UUID id) {
        DataSource dataSource = requireDataSource(id);
        requireJdbc(dataSource);
        try {
            return inspector.listNamespaces(
                            dataSource.getType().name(),
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
        requireJdbc(dataSource);
        try {
            return TableListResponse.from(inspector.listTables(
                    dataSource.getType().name(),
                    dataSource.getConnection().toJdbcConnectionConfig(),
                    new TableQuery(catalog, schema, keyword, includeViews, TABLE_LIST_LIMIT)
            ));
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
    }

    public TableMetadataResponse readTable(UUID id, String catalog, String schema, String table) {
        DataSource dataSource = requireDataSource(id);
        requireJdbc(dataSource);
        JdbcConnectionConfig config = dataSource.getConnection().toJdbcConnectionConfig();
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        try {
            return TableMetadataResponse.from(inspector.readTable(
                    dataSource.getType().name(),
                    config,
                    resolvedTable(dialect, config, catalog, schema, table)
            ));
        } catch (DatabaseAccessException exception) {
            throw remoteAccessException(exception);
        }
    }

    public TablePreviewResponse preview(UUID id, String catalog, String schema, String table, int limit) {
        DataSource dataSource = requireDataSource(id);
        requireJdbc(dataSource);
        JdbcConnectionConfig config = dataSource.getConnection().toJdbcConnectionConfig();
        DatabaseDialect dialect = registry.require(dataSource.getType().name());
        try {
            return TablePreviewResponse.from(inspector.preview(
                    dataSource.getType().name(),
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

    private static JdbcConnectionConfig toConfig(JdbcDataSourceConnectionRequest connection) {
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

    private static JdbcDataSourceConnectionRequest requireJdbc(
            DataSourceType type,
            DataSourceConnectionRequest connection
    ) {
        if (type.connectionKind() != DataSourceConnectionKind.JDBC || !(connection instanceof JdbcDataSourceConnectionRequest jdbc)) {
            throw unsupportedRuntime(type);
        }
        return jdbc;
    }

    private static void requireJdbc(DataSource dataSource) {
        if (!dataSource.getType().isJdbc()) {
            throw unsupportedRuntime(dataSource.getType());
        }
    }

    private static ResponseStatusException unsupportedRuntime(DataSourceType type) {
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, type.displayName() + "连接器尚未实现运行时操作");
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

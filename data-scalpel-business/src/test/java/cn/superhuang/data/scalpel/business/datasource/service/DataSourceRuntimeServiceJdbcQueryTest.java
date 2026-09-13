package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.service.http.HttpApiConnectorRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.BuiltInDialects;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionConfig;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.SpatialColumnMetadata;
import cn.superhuang.data.scalpel.dialect.query.InsertSelectQuery;
import cn.superhuang.data.scalpel.dialect.query.QueryColumn;
import cn.superhuang.data.scalpel.dialect.query.QueryInspection;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlyQueryFingerprint;
import cn.superhuang.data.scalpel.dialect.query.ReadOnlySelectQueryParser;
import cn.superhuang.data.scalpel.dialect.runtime.DatabaseInspector;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcQueryInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Types;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataSourceRuntimeServiceJdbcQueryTest {
    private final DataSourceRepository repository = mock(DataSourceRepository.class);
    private final JdbcQueryInspector queryInspector = mock(JdbcQueryInspector.class);
    private final DataSourceRuntimeService service = new DataSourceRuntimeService(
            repository,
            BuiltInDialects.registry(),
            mock(DatabaseInspector.class),
            queryInspector,
            mock(DataSourceCredentialCipher.class),
            mock(HttpApiConnectorRegistry.class),
            mock(SpatialServiceClient.class)
    );
    private final UUID dataSourceId = UUID.randomUUID();

    @BeforeEach
    void configureSource() {
        when(repository.findById(dataSourceId)).thenReturn(Optional.of(source(true)));
    }

    @Test
    void inspectsOneReadOnlySelectAndHashesTheNormalizedSql() {
        QueryInspection inspection = new QueryInspection(List.of(new QueryColumn(
                "order_id", Types.BIGINT, "int8", LogicalType.INTEGER, false)));
        when(queryInspector.inspect(
                eq("POSTGRESQL"),
                any(JdbcConnectionConfig.class),
                any(InsertSelectQuery.class),
                any(Duration.class)
        )).thenReturn(inspection);

        var response = service.inspectQuery(dataSourceId, "  SELECT order_id FROM orders;  ");

        assertEquals(
                ReadOnlyQueryFingerprint.sha256(
                        ReadOnlySelectQueryParser.parse("SELECT order_id FROM orders")),
                response.analyzedSqlSha256()
        );
        assertEquals(List.of("order_id"), response.columns().stream().map(column -> column.name()).toList());
    }

    @Test
    void acceptsPostgresqlFamilyVendorSources() {
        QueryInspection inspection = new QueryInspection(List.of(new QueryColumn(
                "order_id", Types.BIGINT, "int8", LogicalType.INTEGER, false)));
        for (DataSourceType type : List.of(
                DataSourceType.HIGHGO, DataSourceType.OPENGAUSS, DataSourceType.KINGBASE)) {
            when(repository.findById(dataSourceId)).thenReturn(Optional.of(source(type, true)));
            when(queryInspector.inspect(
                    eq(type.name()),
                    any(JdbcConnectionConfig.class),
                    any(InsertSelectQuery.class),
                    any(Duration.class)
            )).thenReturn(inspection);

            var response = service.inspectQuery(dataSourceId, "SELECT order_id FROM orders");

            assertEquals(List.of("order_id"),
                    response.columns().stream().map(column -> column.name()).toList(), type.name());
        }
    }

    @Test
    void rejectsMutationSqlAndDuplicateOutputNames() {
        assertThrows(ResponseStatusException.class, () ->
                service.inspectQuery(dataSourceId, "DELETE FROM orders"));
        verify(queryInspector, never()).inspect(
                eq("POSTGRESQL"),
                any(JdbcConnectionConfig.class),
                any(InsertSelectQuery.class),
                any(Duration.class)
        );

        when(queryInspector.inspect(
                eq("POSTGRESQL"),
                any(JdbcConnectionConfig.class),
                any(InsertSelectQuery.class),
                any(Duration.class)
        )).thenReturn(new QueryInspection(List.of(
                new QueryColumn("id", Types.BIGINT, "int8", LogicalType.INTEGER, false),
                new QueryColumn("id", Types.BIGINT, "int8", LogicalType.INTEGER, true)
        )));
        ResponseStatusException duplicate = assertThrows(
                ResponseStatusException.class,
                () -> service.inspectQuery(dataSourceId, "SELECT id, parent_id AS id FROM orders")
        );
        assertEquals(HttpStatus.BAD_REQUEST, duplicate.getStatusCode());
    }

    @Test
    void acceptsReadOnlyCteAndRejectsOverlongSqlBeforeJdbc() {
        when(queryInspector.inspect(
                eq("POSTGRESQL"),
                any(JdbcConnectionConfig.class),
                any(InsertSelectQuery.class),
                any(Duration.class)
        )).thenReturn(new QueryInspection(List.of(new QueryColumn(
                "order_id", Types.BIGINT, "int8", LogicalType.INTEGER, false))));

        var response = service.inspectQuery(
                dataSourceId,
                "WITH active_orders AS (SELECT order_id FROM orders) SELECT order_id FROM active_orders"
        );

        assertEquals(List.of("order_id"), response.columns().stream().map(column -> column.name()).toList());
        assertThrows(ResponseStatusException.class, () ->
                service.inspectQuery(dataSourceId, "x".repeat(100_001)));
    }

    @Test
    void rejectsUnsupportedAndGeometryResultColumns() {
        when(queryInspector.inspect(
                eq("POSTGRESQL"),
                any(JdbcConnectionConfig.class),
                any(InsertSelectQuery.class),
                any(Duration.class)
        )).thenReturn(new QueryInspection(List.of(new QueryColumn(
                "unsupported_value", Types.ARRAY, "_int4", LogicalType.ARRAY, true))));
        assertThrows(ResponseStatusException.class, () ->
                service.inspectQuery(dataSourceId, "SELECT unsupported_value FROM source"));

        when(queryInspector.inspect(
                eq("POSTGRESQL"),
                any(JdbcConnectionConfig.class),
                any(InsertSelectQuery.class),
                any(Duration.class)
        )).thenReturn(new QueryInspection(List.of(new QueryColumn(
                "location",
                Types.OTHER,
                "geometry",
                LogicalType.OTHER,
                true,
                new JdbcTypeDescriptor(
                        Types.OTHER,
                        "geometry",
                        null,
                        null,
                        null,
                        null,
                        new SpatialColumnMetadata(
                                "POINT", 4326, "EPSG", 4326,
                                CoordinateDimension.XY, true, true)
                )
        ))));
        assertThrows(ResponseStatusException.class, () ->
                service.inspectQuery(dataSourceId, "SELECT location FROM source"));
    }

    @Test
    void rejectsDisabledSourcesBeforeOpeningJdbc() {
        when(repository.findById(dataSourceId)).thenReturn(Optional.of(source(false)));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.inspectQuery(dataSourceId, "SELECT 1")
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(queryInspector, never()).inspect(
                eq("POSTGRESQL"),
                any(JdbcConnectionConfig.class),
                any(InsertSelectQuery.class),
                any(Duration.class)
        );
    }

    private static DataSource source(boolean enabled) {
        return source(DataSourceType.POSTGRESQL, enabled);
    }

    private static DataSource source(DataSourceType type, boolean enabled) {
        return DataSource.create(
                "order-db",
                "订单库",
                null,
                Set.of(DataSourcePurpose.SOURCE),
                type,
                enabled,
                null,
                DataSourceConnection.jdbc(
                        "localhost", 5432, "orders", "public", "reader", "secret", Map.of())
        );
    }
}

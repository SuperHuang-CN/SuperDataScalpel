package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.dialect.builtin.PostgreSqlDialect;
import cn.superhuang.data.scalpel.dialect.connection.JdbcConnectionFactory;
import cn.superhuang.data.scalpel.dialect.model.JdbcTypeDescriptor;
import cn.superhuang.data.scalpel.dialect.model.LogicalType;
import cn.superhuang.data.scalpel.dialect.model.SpatialColumnMetadata;
import cn.superhuang.data.scalpel.dialect.query.QueryColumn;
import cn.superhuang.data.scalpel.dialect.query.QueryInspection;
import cn.superhuang.data.scalpel.dialect.runtime.JdbcQueryInspector;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SqlServiceDefinitionInspectorTest {

    @Test
    void rejectsGeometryOutputWithAStableSpatialError() {
        JdbcQueryInspector queryInspector = mock(JdbcQueryInspector.class);
        when(queryInspector.inspectPrepared(
                eq("POSTGRESQL"),
                any(),
                any(),
                anyList(),
                any()
        )).thenReturn(new QueryInspection(List.of(new QueryColumn(
                "shape",
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
                                "POINT",
                                990001,
                                "EPSG",
                                4326,
                                CoordinateDimension.XY,
                                true,
                                true
                        )
                )
        ))));
        SqlServiceDefinitionInspector inspector = new SqlServiceDefinitionInspector(
                new DialectRegistry(List.of(new PostgreSqlDialect())),
                new JdbcConnectionFactory(),
                queryInspector
        );

        SqlServiceInspection inspection = inspector.inspect(
                DataSource.create(
                        "warehouse",
                        "数仓",
                        null,
                        Set.of(DataSourcePurpose.SOURCE),
                        DataSourceType.POSTGRESQL,
                        true,
                        null,
                        DataSourceConnection.jdbc(
                                "localhost",
                                5432,
                                "warehouse",
                                "public",
                                "reader",
                                "secret",
                                Map.of()
                        )
                ),
                "SELECT shape FROM spatial_asset",
                List.of()
        );

        assertThat(inspection.valid()).isFalse();
        assertThat(inspection.resultFields()).isEmpty();
        assertThat(inspection.problems())
                .singleElement()
                .extracting(problem -> problem.code())
                .isEqualTo("SPATIAL_FIELD_UNSUPPORTED");
    }
}

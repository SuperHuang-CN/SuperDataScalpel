package cn.superhuang.data.scalpel.engine.datasource;

import cn.superhuang.data.scalpel.contract.service.JdbcPoolMonitorStatus;
import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import cn.superhuang.superops.api.studio.datasource.ApiDataSourceRegistry;
import cn.superhuang.superops.api.studio.datasource.monitoring.DataSourceMonitoringService;
import cn.superhuang.superops.api.studio.datasource.monitoring.PoolMonitorView;
import cn.superhuang.superops.api.studio.entity.DBConfig;
import cn.superhuang.superops.api.studio.entity.vo.DataSourcePoolSummary;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EngineDataSourceMonitoringServiceTest {
    private final ApiDataSourceRegistry registry = mock(ApiDataSourceRegistry.class);
    private final DataSourceMonitoringService monitoring = mock(DataSourceMonitoringService.class);
    private final EngineDataSourceMonitoringService service = new EngineDataSourceMonitoringService(
            registry, monitoring, new EngineProperties("engine_test", "test-token")
    );
    private final UUID id = UUID.randomUUID();

    @Test
    void summarizesUuidRuntimeKeysWithoutExposingSql() {
        when(registry.listNames()).thenReturn(List.of(id.toString(), "standalone-studio"));
        when(monitoring.summary(id.toString())).thenReturn(DataSourcePoolSummary.builder()
                .active(2).maximum(10).idle(3).waiting(1).utilizationPercent(20.0).build());
        var response = service.summaries();
        assertEquals("engine_test", response.engineCode());
        assertEquals(1, response.dataSources().size());
        var entry = response.dataSources().getFirst();
        assertEquals(id, entry.dataSourceId());
        assertEquals(JdbcPoolMonitorStatus.AVAILABLE, entry.status());
        assertEquals(2, entry.pool().active());
        assertNull(entry.pool().acquisitionTimeoutCount());
        verify(monitoring, never()).summary("standalone-studio");
        verify(monitoring, never()).detail(any());
    }

    @Test
    void distinguishesMissingPoolFromDisabledMonitoringWithoutInventingMetrics() {
        when(monitoring.detail(any())).thenReturn(PoolMonitorView.builder().supported(false).capturedAt("now").build());
        assertEquals(JdbcPoolMonitorStatus.NOT_LOADED, service.detail(id).status());
        assertNull(service.detail(id).pool());
        when(registry.contains(id.toString())).thenReturn(true);
        assertEquals(JdbcPoolMonitorStatus.UNSUPPORTED, service.detail(id).status());
    }

    @Test
    void mapsStudioDiagnosticsAndUsesTheSameKeyAsQueryExecution() {
        when(monitoring.detail(any())).thenAnswer(invocation -> {
            DBConfig descriptor = invocation.getArgument(0);
            assertEquals(id.toString(), descriptor.getId());
            assertEquals(id.toString(), descriptor.getName());
            return PoolMonitorView.builder().supported(true).capturedAt("sample")
                    .pool(DataSourcePoolSummary.builder().active(1).longRunningQueryCount(1).build())
                    .activeConnections(List.of(PoolMonitorView.ActiveConnection.builder().connectionId("connection")
                            .state("EXECUTING").sqlPreview("select ?").heldMs(100L).longRunning(true).build()))
                    .recentSql(List.of(PoolMonitorView.RecentSql.builder().fingerprint("fingerprint").failures(1).build()))
                    .incidents(List.of(PoolMonitorView.SaturationIncident.builder().reason("POOL_FULL")
                            .topConsumers(List.of(PoolMonitorView.SqlConsumer.builder().connectionCount(1).build())).build()))
                    .build();
        });
        var response = service.detail(id);
        assertEquals(JdbcPoolMonitorStatus.AVAILABLE, response.status());
        assertEquals("sample", response.capturedAt());
        assertEquals("select ?", response.activeConnections().getFirst().sqlPreview());
        assertTrue(response.activeConnections().getFirst().longRunning());
        assertEquals(1, response.recentSql().getFirst().failures());
        assertEquals("POOL_FULL", response.incidents().getFirst().reason());
        assertEquals(1, response.incidents().getFirst().topConsumers().getFirst().connectionCount());
    }
}

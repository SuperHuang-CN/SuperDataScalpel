package cn.superhuang.data.scalpel.engine.datasource;

import cn.superhuang.data.scalpel.contract.service.EngineDataSourcePoolMonitorResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourcePoolSummariesResponse;
import cn.superhuang.data.scalpel.contract.service.JdbcPoolMonitorStatus;
import cn.superhuang.data.scalpel.contract.service.JdbcPoolSummary;
import cn.superhuang.data.scalpel.engine.config.EngineProperties;
import cn.superhuang.superops.api.studio.datasource.ApiDataSourceRegistry;
import cn.superhuang.superops.api.studio.datasource.monitoring.DataSourceMonitoringService;
import cn.superhuang.superops.api.studio.datasource.monitoring.PoolMonitorView;
import cn.superhuang.superops.api.studio.entity.DBConfig;
import cn.superhuang.superops.api.studio.entity.vo.DataSourcePoolSummary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Reads Studio's existing pool instrumentation. Never creates a pool or executes probe SQL. */
@Service
public class EngineDataSourceMonitoringService {
    private final ApiDataSourceRegistry registry;
    private final DataSourceMonitoringService monitoring;
    private final EngineProperties properties;

    public EngineDataSourceMonitoringService(
            ApiDataSourceRegistry registry, DataSourceMonitoringService monitoring, EngineProperties properties
    ) {
        this.registry = registry;
        this.monitoring = monitoring;
        this.properties = properties;
    }

    public EngineDataSourcePoolSummariesResponse summaries() {
        List<EngineDataSourcePoolSummariesResponse.Entry> entries = new ArrayList<>();
        for (String name : registry.listNames()) {
            UUID id;
            try {
                id = UUID.fromString(name);
            } catch (IllegalArgumentException ignored) {
                continue; // Standalone Studio names are not DataScalpel dataSourceIds.
            }
            if (!id.toString().equals(name)) continue;
            JdbcPoolSummary pool = pool(monitoring.summary(name));
            entries.add(new EngineDataSourcePoolSummariesResponse.Entry(id, status(name, pool != null), pool));
        }
        return new EngineDataSourcePoolSummariesResponse(properties.code(), Instant.now().toString(), entries);
    }

    public EngineDataSourcePoolMonitorResponse detail(UUID dataSourceId) {
        // The execution path resolves exactly this runtime key, even if someone renames a Studio config.
        // This descriptor supplies identity only; it is never persisted or used to establish a connection.
        DBConfig identity = new DBConfig();
        identity.setId(dataSourceId.toString());
        identity.setName(dataSourceId.toString());
        PoolMonitorView view = monitoring.detail(identity);
        return new EngineDataSourcePoolMonitorResponse(
                properties.code(), dataSourceId, status(identity.getName(), view.isSupported()),
                view.getCapturedAt(), pool(view.getPool()),
                map(view.getTopConsumers(), EngineDataSourceMonitoringService::consumer),
                map(view.getActiveConnections(), value -> new EngineDataSourcePoolMonitorResponse.ActiveConnection(
                        value.getConnectionId(), value.getState(), value.getBorrowedAt(), value.getHeldMs(),
                        value.getThreadName(), value.isTransactionActive(), value.getFingerprint(),
                        value.getSqlPreview(), value.getOperation(), value.getSqlStartedAt(), value.getExecutingMs(),
                        value.getLastSqlPreview(), value.getApiId(), value.getApiPath(), value.getExecutionId(),
                        value.isLongRunning(), value.isLongHeld()
                )),
                map(view.getRecentSql(), value -> new EngineDataSourcePoolMonitorResponse.RecentSql(
                        value.getFingerprint(), value.getSqlPreview(), value.getOperation(), value.getExecutions(),
                        value.getFailures(), value.getTotalDurationMs(), value.getMaxDurationMs(), value.getLastExecutedAt()
                )),
                map(view.getIncidents(), value -> new EngineDataSourcePoolMonitorResponse.SaturationIncident(
                        value.getOccurredAt(), value.getReason(), value.getRequestedSqlPreview(),
                        value.getRequestedExecutionId(), pool(value.getPool()),
                        map(value.getTopConsumers(), EngineDataSourceMonitoringService::consumer)
                ))
        );
    }

    private JdbcPoolMonitorStatus status(String name, boolean supported) {
        if (supported) return JdbcPoolMonitorStatus.AVAILABLE;
        return registry.contains(name) ? JdbcPoolMonitorStatus.UNSUPPORTED : JdbcPoolMonitorStatus.NOT_LOADED;
    }

    private static JdbcPoolSummary pool(DataSourcePoolSummary value) {
        if (value == null) return null;
        return new JdbcPoolSummary(
                value.getPoolName(), value.getMaximum(), value.getTotal(), value.getActive(), value.getIdle(),
                value.getWaiting(), value.getUtilizationPercent(), value.getAcquisitionTimeoutCount(),
                value.getLastSaturationAt(), value.getExecutingConnections(), value.getIdleInTransactionConnections(),
                value.getBorrowedIdleConnections(), value.getLongRunningQueryCount(), value.getLongHeldConnectionCount()
        );
    }

    private static EngineDataSourcePoolMonitorResponse.SqlConsumer consumer(PoolMonitorView.SqlConsumer value) {
        return new EngineDataSourcePoolMonitorResponse.SqlConsumer(
                value.getFingerprint(), value.getSqlPreview(), value.getOperation(), value.getConnectionCount(),
                value.getTotalHeldMs(), value.getMaxHeldMs(), value.getMaxExecutingMs()
        );
    }

    private static <T, R> List<R> map(List<T> values, Function<T, R> mapper) {
        return values == null ? List.of() : values.stream().map(mapper).toList();
    }
}

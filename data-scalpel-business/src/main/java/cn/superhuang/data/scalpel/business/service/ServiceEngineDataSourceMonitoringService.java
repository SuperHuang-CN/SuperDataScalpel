package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineDataSourceRegistrationRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourcePoolMonitorResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourcePoolSummariesResponse;
import cn.superhuang.data.scalpel.contract.service.JdbcPoolMonitorStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/** Proxies read-only runtime diagnostics without changing registration or health state. */
@Service
public class ServiceEngineDataSourceMonitoringService {
    private final ServiceEngineRepository engines;
    private final ServiceEngineDataSourceRegistrationRepository registrations;
    private final ServiceEngineClient client;
    private final TransactionTemplate readTransaction;

    public ServiceEngineDataSourceMonitoringService(
            ServiceEngineRepository engines, ServiceEngineDataSourceRegistrationRepository registrations,
            ServiceEngineClient client, PlatformTransactionManager transactionManager
    ) {
        this.engines = engines;
        this.registrations = registrations;
        this.client = client;
        this.readTransaction = new TransactionTemplate(transactionManager);
        this.readTransaction.setReadOnly(true);
    }

    public EngineDataSourcePoolSummariesResponse summaries(UUID engineId) {
        Snapshot snapshot = readTransaction.execute(status -> new Snapshot(
                requireEngine(engineId), registrations.findAllByEngineId(engineId).stream()
                        .map(registration -> registration.getDataSourceId()).toList()
        ));
        if (snapshot.dataSourceIds().isEmpty()) {
            return new EngineDataSourcePoolSummariesResponse(snapshot.engine().getCode(), Instant.now().toString(), List.of());
        }
        // The remote call occurs after the short management-database transaction has ended.
        EngineDataSourcePoolSummariesResponse response = remote(() -> client.dataSourcePoolSummaries(snapshot.engine()));
        if (response == null || !snapshot.engine().matchesCode(response.engineCode())
                || response.dataSources() == null || response.capturedAt() == null) {
            throw invalidResponse();
        }
        var byId = new HashMap<UUID, EngineDataSourcePoolSummariesResponse.Entry>();
        for (var entry : response.dataSources()) {
            if (entry == null || entry.dataSourceId() == null || entry.status() == null
                    || (entry.status() == JdbcPoolMonitorStatus.AVAILABLE && entry.pool() == null)
                    || byId.put(entry.dataSourceId(), entry) != null) throw invalidResponse();
        }
        // Never expose standalone Studio data sources that are not registered to this Engine in Admin.
        return new EngineDataSourcePoolSummariesResponse(response.engineCode(), response.capturedAt(),
                snapshot.dataSourceIds().stream().map(id -> byId.getOrDefault(id,
                        new EngineDataSourcePoolSummariesResponse.Entry(id, JdbcPoolMonitorStatus.NOT_LOADED, null)
                )).toList());
    }

    public EngineDataSourcePoolMonitorResponse detail(UUID registrationId) {
        Snapshot snapshot = readTransaction.execute(status -> {
            var registration = registrations.findById(registrationId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "服务引擎数据源注册不存在"));
            return new Snapshot(requireEngine(registration.getEngineId()), List.of(registration.getDataSourceId()));
        });
        UUID dataSourceId = snapshot.dataSourceIds().getFirst();
        EngineDataSourcePoolMonitorResponse response = remote(() -> client.dataSourcePoolMonitor(snapshot.engine(), dataSourceId));
        if (response == null || !snapshot.engine().matchesCode(response.engineCode())
                || !dataSourceId.equals(response.dataSourceId()) || response.status() == null
                || response.capturedAt() == null
                || (response.status() == JdbcPoolMonitorStatus.AVAILABLE && response.pool() == null)
                || response.topConsumers() == null || response.activeConnections() == null
                || response.recentSql() == null || response.incidents() == null) throw invalidResponse();
        return response;
    }

    private ServiceEngine requireEngine(UUID id) {
        ServiceEngine engine = engines.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "服务引擎不存在"));
        if (engine.getType() != ServiceEngineType.DATASCALPEL) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "GeoServer 引擎不提供 API Studio JDBC 监控");
        }
        return engine; // Disabled Engines remain readable.
    }

    private <T> T remote(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            String detail = switch (exception.getStatusCode().value()) {
                case 404 -> "引擎未提供 JDBC 监控接口，请升级并重启 Service Engine";
                case 401, 403 -> "无法读取 JDBC 监控，请检查引擎 Management Token 配置";
                default -> "引擎 JDBC 监控暂不可用，请稍后重试";
            };
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, detail, exception);
        } catch (RestClientException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "无法连接引擎或读取 JDBC 监控超时，请检查引擎地址和运行状态", exception);
        }
    }

    private ResponseStatusException invalidResponse() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY, "引擎 JDBC 监控响应无效或标识不匹配，请检查引擎地址和版本");
    }

    private record Snapshot(ServiceEngine engine, List<UUID> dataSourceIds) {
    }
}

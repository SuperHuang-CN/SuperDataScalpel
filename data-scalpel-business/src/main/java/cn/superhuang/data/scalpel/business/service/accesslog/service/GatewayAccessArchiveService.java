package cn.superhuang.data.scalpel.business.service.accesslog.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class GatewayAccessArchiveService {

    private static final Logger log = LoggerFactory.getLogger(GatewayAccessArchiveService.class);

    private static final String FIND_CANDIDATE_HOURS_SQL = """
            select hour_start
            from ds_gateway_access_hour_state
            where status in ('DIRTY', 'FAILED')
              and hour_start < ?
            order by hour_start
            limit ?
            """;

    private static final String LOCK_HOUR_STATUS_SQL = """
            select status
            from ds_gateway_access_hour_state
            where hour_start = ?
            for update
            """;

    private static final String INSERT_SERVICE_HOURLY_SQL = """
            insert into ds_gateway_access_service_hourly (
                id, created_at, updated_at,
                hour_start, gateway_provider, data_service_id,
                request_count,
                status_2xx_count, status_3xx_count, status_4xx_count, status_5xx_count,
                status_401_count, status_403_count, status_429_count,
                gateway_rejected_count, gateway_error_count, upstream_error_count,
                request_bytes_sum, response_bytes_sum,
                request_latency_sample_count, request_latency_sum_ms, request_latency_max_ms,
                request_latency_p95_ms, request_latency_p99_ms,
                proxy_latency_sample_count, proxy_latency_sum_ms,
                proxy_latency_p95_ms, proxy_latency_p99_ms
            )
            select
                md5(concat(
                    'service|', gateway_provider, '|', data_service_id::text, '|', ?::text
                ))::uuid,
                ?, ?,
                ?, gateway_provider, data_service_id,
                count(*),
                count(*) filter (where response_status between 200 and 299),
                count(*) filter (where response_status between 300 and 399),
                count(*) filter (where response_status between 400 and 499),
                count(*) filter (where response_status between 500 and 599),
                count(*) filter (where response_status = 401),
                count(*) filter (where response_status = 403),
                count(*) filter (where response_status = 429),
                count(*) filter (where gateway_rejected),
                count(*) filter (where gateway_error),
                count(*) filter (where upstream_error),
                coalesce(sum(request_size_bytes), 0)::bigint,
                coalesce(sum(response_size_bytes), 0)::bigint,
                count(request_latency_ms),
                coalesce(sum(request_latency_ms), 0)::bigint,
                max(request_latency_ms),
                percentile_cont(0.95) within group (order by request_latency_ms)
                    filter (where request_latency_ms is not null),
                percentile_cont(0.99) within group (order by request_latency_ms)
                    filter (where request_latency_ms is not null),
                count(proxy_latency_ms),
                coalesce(sum(proxy_latency_ms), 0)::bigint,
                percentile_cont(0.95) within group (order by proxy_latency_ms)
                    filter (where proxy_latency_ms is not null),
                percentile_cont(0.99) within group (order by proxy_latency_ms)
                    filter (where proxy_latency_ms is not null)
            from ds_gateway_access_log
            where occurred_at >= ?
              and occurred_at < ?
              and data_service_id is not null
              and identity_resolution_status <> 'IDENTITY_MISMATCH'
            group by gateway_provider, data_service_id
            """;

    private static final String INSERT_CONSUMER_HOURLY_SQL = """
            insert into ds_gateway_access_consumer_service_hourly (
                id, created_at, updated_at,
                hour_start, gateway_provider, data_service_id, consumer_id,
                request_count,
                status_2xx_count, status_3xx_count, status_4xx_count, status_5xx_count,
                status_401_count, status_403_count, status_429_count,
                gateway_rejected_count, gateway_error_count, upstream_error_count,
                request_bytes_sum, response_bytes_sum,
                request_latency_sample_count, request_latency_sum_ms, request_latency_max_ms,
                request_latency_p95_ms, request_latency_p99_ms,
                proxy_latency_sample_count, proxy_latency_sum_ms,
                proxy_latency_p95_ms, proxy_latency_p99_ms
            )
            select
                md5(concat(
                    'consumer|', gateway_provider, '|', data_service_id::text,
                    '|', consumer_id::text, '|', ?::text
                ))::uuid,
                ?, ?,
                ?, gateway_provider, data_service_id, consumer_id,
                count(*),
                count(*) filter (where response_status between 200 and 299),
                count(*) filter (where response_status between 300 and 399),
                count(*) filter (where response_status between 400 and 499),
                count(*) filter (where response_status between 500 and 599),
                count(*) filter (where response_status = 401),
                count(*) filter (where response_status = 403),
                count(*) filter (where response_status = 429),
                count(*) filter (where gateway_rejected),
                count(*) filter (where gateway_error),
                count(*) filter (where upstream_error),
                coalesce(sum(request_size_bytes), 0)::bigint,
                coalesce(sum(response_size_bytes), 0)::bigint,
                count(request_latency_ms),
                coalesce(sum(request_latency_ms), 0)::bigint,
                max(request_latency_ms),
                percentile_cont(0.95) within group (order by request_latency_ms)
                    filter (where request_latency_ms is not null),
                percentile_cont(0.99) within group (order by request_latency_ms)
                    filter (where request_latency_ms is not null),
                count(proxy_latency_ms),
                coalesce(sum(proxy_latency_ms), 0)::bigint,
                percentile_cont(0.95) within group (order by proxy_latency_ms)
                    filter (where proxy_latency_ms is not null),
                percentile_cont(0.99) within group (order by proxy_latency_ms)
                    filter (where proxy_latency_ms is not null)
            from ds_gateway_access_log
            where occurred_at >= ?
              and occurred_at < ?
              and data_service_id is not null
              and consumer_id is not null
              and identity_resolution_status <> 'IDENTITY_MISMATCH'
            group by gateway_provider, data_service_id, consumer_id
            """;

    private static final String DELETE_RAW_BATCH_SQL = """
            with expired as (
                select id
                from ds_gateway_access_log
                where occurred_at < ?
                order by occurred_at
                limit ?
            )
            delete from ds_gateway_access_log access_log
            using expired
            where access_log.id = expired.id
            """;

    private final GatewayAccessProperties properties;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    public GatewayAccessArchiveService(
            GatewayAccessProperties properties,
            JdbcTemplate jdbcTemplate,
            PlatformTransactionManager transactionManager
    ) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public int archiveClosedHours() {
        Instant currentHour = Instant.now().truncatedTo(ChronoUnit.HOURS);
        List<Instant> candidates = jdbcTemplate.query(
                FIND_CANDIDATE_HOURS_SQL,
                (resultSet, rowNumber) -> resultSet.getTimestamp("hour_start").toInstant(),
                timestamp(currentHour),
                properties.archiveMaxHours()
        );
        int archived = 0;
        for (Instant hour : candidates) {
            try {
                Boolean changed = transactionTemplate.execute(status -> rebuildHour(hour));
                if (Boolean.TRUE.equals(changed)) {
                    archived++;
                }
            } catch (RuntimeException exception) {
                markFailed(hour, exception);
                log.warn("网关访问日志小时归档失败：hour={}", hour, exception);
            }
        }
        return archived;
    }

    public int cleanExpiredRawLogs() {
        Instant cutoff = Instant.now().minus(properties.rawRetention());
        int deletedTotal = 0;
        for (int batch = 0; batch < properties.cleanupMaxBatches(); batch++) {
            Integer deleted = transactionTemplate.execute(status -> jdbcTemplate.update(
                    DELETE_RAW_BATCH_SQL,
                    timestamp(cutoff),
                    properties.cleanupBatchSize()
            ));
            int deletedCount = deleted == null ? 0 : deleted;
            deletedTotal += deletedCount;
            if (deletedCount < properties.cleanupBatchSize()) {
                break;
            }
        }
        return deletedTotal;
    }

    public int cleanExpiredHourlyStatistics() {
        Instant cutoff = Instant.now()
                .minus(properties.hourlyRetention())
                .truncatedTo(ChronoUnit.HOURS);
        Integer deleted = transactionTemplate.execute(status -> {
            int total = jdbcTemplate.update(
                    "delete from ds_gateway_access_consumer_service_hourly where hour_start < ?",
                    timestamp(cutoff)
            );
            total += jdbcTemplate.update(
                    "delete from ds_gateway_access_service_hourly where hour_start < ?",
                    timestamp(cutoff)
            );
            total += jdbcTemplate.update(
                    "delete from ds_gateway_access_hour_state where hour_start < ?",
                    timestamp(cutoff)
            );
            return total;
        });
        return deleted == null ? 0 : deleted;
    }

    private boolean rebuildHour(Instant hour) {
        List<String> statuses = jdbcTemplate.query(
                LOCK_HOUR_STATUS_SQL,
                (resultSet, rowNumber) -> resultSet.getString("status"),
                timestamp(hour)
        );
        if (statuses.isEmpty() || "SUCCEEDED".equals(statuses.getFirst())) {
            return false;
        }

        Instant hourEnd = hour.plus(1, ChronoUnit.HOURS);
        Instant now = Instant.now();
        jdbcTemplate.update(
                "delete from ds_gateway_access_consumer_service_hourly where hour_start = ?",
                timestamp(hour)
        );
        jdbcTemplate.update(
                "delete from ds_gateway_access_service_hourly where hour_start = ?",
                timestamp(hour)
        );
        insertServiceStatistics(hour, hourEnd, now);
        insertConsumerStatistics(hour, hourEnd, now);
        jdbcTemplate.update(
                """
                update ds_gateway_access_hour_state
                set status = 'SUCCEEDED',
                    last_aggregated_at = ?,
                    last_error = null,
                    updated_at = ?
                where hour_start = ?
                """,
                timestamp(now),
                timestamp(now),
                timestamp(hour)
        );
        return true;
    }

    private void insertServiceStatistics(Instant hour, Instant hourEnd, Instant now) {
        Timestamp hourTimestamp = timestamp(hour);
        Timestamp nowTimestamp = timestamp(now);
        jdbcTemplate.update(
                INSERT_SERVICE_HOURLY_SQL,
                hourTimestamp,
                nowTimestamp,
                nowTimestamp,
                hourTimestamp,
                hourTimestamp,
                timestamp(hourEnd)
        );
    }

    private void insertConsumerStatistics(Instant hour, Instant hourEnd, Instant now) {
        Timestamp hourTimestamp = timestamp(hour);
        Timestamp nowTimestamp = timestamp(now);
        jdbcTemplate.update(
                INSERT_CONSUMER_HOURLY_SQL,
                hourTimestamp,
                nowTimestamp,
                nowTimestamp,
                hourTimestamp,
                hourTimestamp,
                timestamp(hourEnd)
        );
    }

    private void markFailed(Instant hour, RuntimeException exception) {
        String error = safeError(exception);
        try {
            transactionTemplate.executeWithoutResult(status -> jdbcTemplate.update(
                    """
                    update ds_gateway_access_hour_state
                    set status = 'FAILED',
                        last_error = ?,
                        updated_at = ?
                    where hour_start = ?
                    """,
                    error,
                    timestamp(Instant.now()),
                    timestamp(hour)
            ));
        } catch (RuntimeException updateException) {
            log.warn("无法记录网关访问日志小时归档失败状态：hour={}", hour, updateException);
        }
    }

    private static String safeError(RuntimeException exception) {
        String value = exception.getMessage();
        if (value == null || value.isBlank()) {
            value = "小时归档失败";
        }
        value = value.replaceAll("[\\r\\n\\t]+", " ");
        return value.substring(0, Math.min(1000, value.length()));
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}

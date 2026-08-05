package cn.superhuang.data.scalpel.business.service.accesslog.service;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class GatewayAccessLogStore {

    private static final String INSERT_LOG_SQL = """
            insert into ds_gateway_access_log (
                id, created_at, updated_at,
                event_id, schema_version, gateway_provider,
                occurred_at, observed_at, received_at,
                data_service_id, gateway_service_id, gateway_service_name,
                gateway_route_id, gateway_route_name,
                consumer_id, gateway_consumer_id, consumer_code,
                gateway_credential_external_id,
                gateway_request_id, request_method, request_path,
                response_status, upstream_status,
                request_size_bytes, response_size_bytes,
                request_latency_ms, kong_latency_ms, proxy_latency_ms, receive_latency_ms,
                client_ip, identity_resolution_status,
                gateway_rejected, gateway_error, upstream_error,
                kafka_topic, kafka_partition, kafka_offset
            ) values (
                ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?,
                ?, ?,
                ?, ?, ?,
                ?,
                ?, ?, ?,
                ?, ?,
                ?, ?,
                ?, ?, ?, ?,
                ?, ?,
                ?, ?, ?,
                ?, ?, ?
            )
            on conflict do nothing
            """;

    private static final String MARK_HOUR_DIRTY_SQL = """
            insert into ds_gateway_access_hour_state (
                id, created_at, updated_at, hour_start, status,
                last_event_received_at, last_aggregated_at, last_error
            ) values (?, ?, ?, ?, 'DIRTY', ?, null, null)
            on conflict (hour_start) do update set
                status = 'DIRTY',
                last_event_received_at = greatest(
                    ds_gateway_access_hour_state.last_event_received_at,
                    excluded.last_event_received_at
                ),
                last_error = null,
                updated_at = excluded.updated_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public GatewayAccessLogStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void append(List<GatewayAccessRecord> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate(INSERT_LOG_SQL, new LogBatch(records));
        markHoursDirty(records);
    }

    private void markHoursDirty(List<GatewayAccessRecord> records) {
        Set<Instant> hours = new LinkedHashSet<>();
        Instant receivedAt = Instant.EPOCH;
        for (GatewayAccessRecord record : records) {
            hours.add(record.occurredAt().truncatedTo(ChronoUnit.HOURS));
            if (record.receivedAt().isAfter(receivedAt)) {
                receivedAt = record.receivedAt();
            }
        }
        Timestamp now = timestamp(receivedAt);
        for (Instant hour : hours) {
            jdbcTemplate.update(
                    MARK_HOUR_DIRTY_SQL,
                    UUID.randomUUID(),
                    now,
                    now,
                    timestamp(hour),
                    now
            );
        }
    }

    private static final class LogBatch implements BatchPreparedStatementSetter {

        private final List<GatewayAccessRecord> records;

        private LogBatch(List<GatewayAccessRecord> records) {
            this.records = records;
        }

        @Override
        public void setValues(PreparedStatement statement, int index) throws SQLException {
            GatewayAccessRecord record = records.get(index);
            Timestamp receivedAt = timestamp(record.receivedAt());
            int column = 1;
            statement.setObject(column++, UUID.randomUUID());
            statement.setTimestamp(column++, receivedAt);
            statement.setTimestamp(column++, receivedAt);
            statement.setObject(column++, record.eventId());
            statement.setString(column++, record.schemaVersion());
            statement.setString(column++, record.gatewayProvider().name());
            statement.setTimestamp(column++, timestamp(record.occurredAt()));
            setTimestamp(statement, column++, record.observedAt());
            statement.setTimestamp(column++, receivedAt);
            statement.setObject(column++, record.dataServiceId());
            statement.setString(column++, record.gatewayServiceId());
            statement.setString(column++, record.gatewayServiceName());
            statement.setString(column++, record.gatewayRouteId());
            statement.setString(column++, record.gatewayRouteName());
            statement.setObject(column++, record.consumerId());
            statement.setString(column++, record.gatewayConsumerId());
            statement.setString(column++, record.consumerCode());
            statement.setString(column++, record.gatewayCredentialExternalId());
            statement.setString(column++, record.gatewayRequestId());
            statement.setString(column++, record.requestMethod());
            statement.setString(column++, record.requestPath());
            statement.setInt(column++, record.responseStatus());
            statement.setString(column++, record.upstreamStatus());
            setLong(statement, column++, record.requestSizeBytes());
            setLong(statement, column++, record.responseSizeBytes());
            setLong(statement, column++, record.requestLatencyMs());
            setLong(statement, column++, record.kongLatencyMs());
            setLong(statement, column++, record.proxyLatencyMs());
            setLong(statement, column++, record.receiveLatencyMs());
            statement.setString(column++, record.clientIp());
            statement.setString(column++, record.identityResolutionStatus().name());
            statement.setBoolean(column++, record.gatewayRejected());
            statement.setBoolean(column++, record.gatewayError());
            statement.setBoolean(column++, record.upstreamError());
            statement.setString(column++, record.kafkaTopic());
            statement.setInt(column++, record.kafkaPartition());
            statement.setLong(column, record.kafkaOffset());
        }

        @Override
        public int getBatchSize() {
            return records.size();
        }
    }

    private static void setLong(PreparedStatement statement, int column, Long value) throws SQLException {
        if (value == null) {
            statement.setObject(column, null);
        } else {
            statement.setLong(column, value);
        }
    }

    private static void setTimestamp(PreparedStatement statement, int column, Instant value) throws SQLException {
        if (value == null) {
            statement.setObject(column, null);
        } else {
            statement.setTimestamp(column, timestamp(value));
        }
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }
}

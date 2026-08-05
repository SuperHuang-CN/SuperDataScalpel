package cn.superhuang.data.scalpel.business.service.accesslog.service;

import cn.superhuang.data.scalpel.business.service.accesslog.domain.GatewayAccessIdentityResolutionStatus;
import cn.superhuang.data.scalpel.business.service.accesslog.web.request.GatewayAccessRankingDimension;
import cn.superhuang.data.scalpel.business.service.accesslog.web.request.GatewayAccessRankingMetric;
import cn.superhuang.data.scalpel.business.service.accesslog.web.response.GatewayAccessHourlyStatResponse;
import cn.superhuang.data.scalpel.business.service.accesslog.web.response.GatewayAccessLogResponse;
import cn.superhuang.data.scalpel.business.service.accesslog.web.response.GatewayAccessOverviewResponse;
import cn.superhuang.data.scalpel.business.service.accesslog.web.response.GatewayAccessRankingResponse;
import cn.superhuang.data.scalpel.business.service.accesslog.web.response.GatewayAccessTrendPointResponse;
import cn.superhuang.data.scalpel.business.service.accesslog.web.response.GatewayAccessTrendResponse;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class GatewayAccessQueryService {

    private static final String HOURLY_COLUMNS = """
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
            """;

    private static final String OVERVIEW_COLUMNS = """
            coalesce(sum(request_count), 0) as request_count,
            coalesce(sum(status_2xx_count), 0) as status_2xx_count,
            coalesce(sum(status_3xx_count), 0) as status_3xx_count,
            coalesce(sum(status_4xx_count), 0) as status_4xx_count,
            coalesce(sum(status_5xx_count), 0) as status_5xx_count,
            coalesce(sum(status_401_count), 0) as status_401_count,
            coalesce(sum(status_403_count), 0) as status_403_count,
            coalesce(sum(status_429_count), 0) as status_429_count,
            coalesce(sum(gateway_rejected_count), 0) as gateway_rejected_count,
            coalesce(sum(gateway_error_count), 0) as gateway_error_count,
            coalesce(sum(upstream_error_count), 0) as upstream_error_count,
            coalesce(sum(request_bytes_sum), 0) as request_bytes_sum,
            coalesce(sum(response_bytes_sum), 0) as response_bytes_sum,
            coalesce(sum(request_latency_sample_count), 0) as request_latency_sample_count,
            coalesce(sum(request_latency_sum_ms), 0) as request_latency_sum_ms,
            max(request_latency_max_ms) as request_latency_max_ms,
            max(request_latency_p95_ms) as request_latency_p95_ms,
            max(request_latency_p99_ms) as request_latency_p99_ms,
            coalesce(sum(proxy_latency_sample_count), 0) as proxy_latency_sample_count,
            coalesce(sum(proxy_latency_sum_ms), 0) as proxy_latency_sum_ms,
            max(proxy_latency_p95_ms) as proxy_latency_p95_ms,
            max(proxy_latency_p99_ms) as proxy_latency_p99_ms
            """;

    private final GatewayAccessProperties properties;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public GatewayAccessQueryService(
            GatewayAccessProperties properties,
            NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.properties = properties;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional(readOnly = true)
    public PageResponse<GatewayAccessLogResponse> searchLogs(
            Instant requestedFrom,
            Instant requestedTo,
            UUID dataServiceId,
            UUID consumerId,
            Integer responseStatus,
            String gatewayRequestId,
            boolean abnormalOnly,
            int page,
            int size
    ) {
        if (page < 0) {
            throw badRequest("page 不能小于 0");
        }
        if (size < 1 || size > 200) {
            throw badRequest("size 必须在 1 到 200 之间");
        }
        TimeWindow window = rawWindow(requestedFrom, requestedTo);
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("from", timestamp(window.fromInclusive()))
                .addValue("to", timestamp(window.toExclusive()));
        String where = rawWhere(
                parameters,
                dataServiceId,
                consumerId,
                responseStatus,
                gatewayRequestId,
                abnormalOnly
        );

        long total = requiredLong(jdbcTemplate.queryForObject(
                "select count(*) from ds_gateway_access_log l " + where,
                parameters,
                Long.class
        ));
        parameters.addValue("limit", size).addValue("offset", (long) page * size);
        List<GatewayAccessLogResponse> content = jdbcTemplate.query(
                """
                select
                    l.*,
                    data_service.code as current_data_service_code,
                    data_service.name as current_data_service_name,
                    api_consumer.name as current_consumer_name
                from ds_gateway_access_log l
                left join ds_data_service data_service on data_service.id = l.data_service_id
                left join ds_api_consumer api_consumer on api_consumer.id = l.consumer_id
                %s
                order by l.occurred_at desc, l.id desc
                limit :limit offset :offset
                """.formatted(where),
                parameters,
                GatewayAccessQueryService::mapLog
        );
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResponse<>(content, total, totalPages, page, size);
    }

    @Transactional(readOnly = true)
    public GatewayAccessOverviewResponse overview(
            Instant requestedFrom,
            Instant requestedTo,
            UUID dataServiceId,
            UUID consumerId
    ) {
        TimeWindow window = hourlyWindow(requestedFrom, requestedTo);
        boolean consumerView = consumerId != null;
        String table = consumerView
                ? "ds_gateway_access_consumer_service_hourly"
                : "ds_gateway_access_service_hourly";
        MapSqlParameterSource parameters = hourlyParameters(window);
        StringBuilder where = new StringBuilder(
                " where hour_start >= :from and hour_start < :to"
        );
        if (dataServiceId != null) {
            where.append(" and data_service_id = :dataServiceId");
            parameters.addValue("dataServiceId", dataServiceId);
        }
        if (consumerView) {
            where.append(" and consumer_id = :consumerId");
            parameters.addValue("consumerId", consumerId);
        }
        return jdbcTemplate.queryForObject(
                "select " + OVERVIEW_COLUMNS + " from " + table + where,
                parameters,
                (resultSet, rowNumber) -> mapOverview(resultSet, window)
        );
    }

    @Transactional(readOnly = true)
    public GatewayAccessTrendResponse hourlyTrend(
            Instant requestedFrom,
            Instant requestedTo,
            UUID dataServiceId,
            UUID consumerId
    ) {
        TimeWindow window = hourlyWindow(requestedFrom, requestedTo);
        boolean consumerView = consumerId != null;
        String table = consumerView
                ? "ds_gateway_access_consumer_service_hourly"
                : "ds_gateway_access_service_hourly";
        MapSqlParameterSource parameters = hourlyParameters(window);
        StringBuilder where = new StringBuilder(
                " where hour_start >= :from and hour_start < :to"
        );
        if (dataServiceId != null) {
            where.append(" and data_service_id = :dataServiceId");
            parameters.addValue("dataServiceId", dataServiceId);
        }
        if (consumerView) {
            where.append(" and consumer_id = :consumerId");
            parameters.addValue("consumerId", consumerId);
        }
        List<GatewayAccessTrendPointResponse> points = jdbcTemplate.query(
                """
                select
                    hour_start,
                    %s
                from %s
                %s
                group by hour_start
                order by hour_start
                """.formatted(OVERVIEW_COLUMNS, table, where),
                parameters,
                GatewayAccessQueryService::mapTrendPoint
        );
        return new GatewayAccessTrendResponse(
                window.fromInclusive(),
                window.toExclusive(),
                points
        );
    }

    @Transactional(readOnly = true)
    public List<GatewayAccessHourlyStatResponse> serviceHourly(
            Instant requestedFrom,
            Instant requestedTo,
            UUID dataServiceId
    ) {
        if (dataServiceId == null) {
            throw badRequest("dataServiceId 不能为空");
        }
        TimeWindow window = hourlyWindow(requestedFrom, requestedTo);
        MapSqlParameterSource parameters = hourlyParameters(window)
                .addValue("dataServiceId", dataServiceId);
        return jdbcTemplate.query(
                """
                select %s
                from ds_gateway_access_service_hourly
                where hour_start >= :from
                  and hour_start < :to
                  and data_service_id = :dataServiceId
                order by hour_start, gateway_provider
                """.formatted(HOURLY_COLUMNS),
                parameters,
                (resultSet, rowNumber) -> mapHourly(resultSet, null)
        );
    }

    @Transactional(readOnly = true)
    public List<GatewayAccessHourlyStatResponse> consumerHourly(
            Instant requestedFrom,
            Instant requestedTo,
            UUID consumerId,
            UUID dataServiceId
    ) {
        if (consumerId == null) {
            throw badRequest("consumerId 不能为空");
        }
        TimeWindow window = hourlyWindow(requestedFrom, requestedTo);
        MapSqlParameterSource parameters = hourlyParameters(window)
                .addValue("consumerId", consumerId);
        String serviceFilter = "";
        if (dataServiceId != null) {
            serviceFilter = " and data_service_id = :dataServiceId";
            parameters.addValue("dataServiceId", dataServiceId);
        }
        return jdbcTemplate.query(
                """
                select %s, consumer_id
                from ds_gateway_access_consumer_service_hourly
                where hour_start >= :from
                  and hour_start < :to
                  and consumer_id = :consumerId
                  %s
                order by hour_start, data_service_id, gateway_provider
                """.formatted(HOURLY_COLUMNS, serviceFilter),
                parameters,
                (resultSet, rowNumber) -> mapHourly(resultSet, resultSet.getObject("consumer_id", UUID.class))
        );
    }

    @Transactional(readOnly = true)
    public List<GatewayAccessRankingResponse> rankings(
            Instant requestedFrom,
            Instant requestedTo,
            GatewayAccessRankingDimension dimension,
            GatewayAccessRankingMetric metric,
            UUID dataServiceId,
            UUID consumerId,
            int limit
    ) {
        if (dimension == null) {
            throw badRequest("dimension 不能为空");
        }
        if (metric == null) {
            throw badRequest("metric 不能为空");
        }
        if (limit < 1 || limit > 100) {
            throw badRequest("limit 必须在 1 到 100 之间");
        }
        TimeWindow window = hourlyWindow(requestedFrom, requestedTo);
        boolean serviceDimension = dimension == GatewayAccessRankingDimension.SERVICE;
        boolean consumerTable = !serviceDimension || consumerId != null;
        String table = consumerTable
                ? "ds_gateway_access_consumer_service_hourly"
                : "ds_gateway_access_service_hourly";
        String subjectColumn = serviceDimension
                ? "stats.data_service_id"
                : "stats.consumer_id";
        String subjectTable = serviceDimension ? "ds_data_service" : "ds_api_consumer";
        String orderColumn = switch (metric) {
            case REQUEST_COUNT -> "request_count";
            case SERVER_ERROR_COUNT -> "server_error_count";
            case P95_LATENCY -> "peak_p95";
        };
        MapSqlParameterSource parameters = hourlyParameters(window).addValue("limit", limit);
        StringBuilder where = new StringBuilder(
                " where stats.hour_start >= :from and stats.hour_start < :to"
        );
        if (dataServiceId != null) {
            where.append(" and stats.data_service_id = :dataServiceId");
            parameters.addValue("dataServiceId", dataServiceId);
        }
        if (consumerTable && consumerId != null) {
            where.append(" and stats.consumer_id = :consumerId");
            parameters.addValue("consumerId", consumerId);
        }
        return jdbcTemplate.query(
                """
                select
                    %s as subject_id,
                    subject.code as subject_code,
                    subject.name as subject_name,
                    sum(stats.request_count) as request_count,
                    sum(stats.status_2xx_count) as status_2xx_count,
                    sum(stats.status_4xx_count) as status_4xx_count,
                    sum(stats.status_5xx_count) as status_5xx_count,
                    sum(stats.status_5xx_count) as server_error_count,
                    max(stats.request_latency_p95_ms) as peak_p95
                from %s stats
                left join %s subject on subject.id = %s
                %s
                group by %s, subject.code, subject.name
                order by %s desc nulls last, subject_id
                limit :limit
                """.formatted(
                        subjectColumn,
                        table,
                        subjectTable,
                        subjectColumn,
                        where,
                        subjectColumn,
                        orderColumn
                ),
                parameters,
                (resultSet, rowNumber) -> new GatewayAccessRankingResponse(
                        dimension,
                        resultSet.getObject("subject_id", UUID.class),
                        resultSet.getString("subject_code"),
                        resultSet.getString("subject_name"),
                        resultSet.getLong("request_count"),
                        resultSet.getLong("status_2xx_count"),
                        resultSet.getLong("status_4xx_count"),
                        resultSet.getLong("status_5xx_count"),
                        resultSet.getLong("server_error_count"),
                        nullableDouble(resultSet, "peak_p95")
                )
        );
    }

    private TimeWindow rawWindow(Instant requestedFrom, Instant requestedTo) {
        Instant to = requestedTo == null ? Instant.now() : requestedTo;
        Instant from = requestedFrom == null ? to.minus(1, ChronoUnit.HOURS) : requestedFrom;
        validateWindow(from, to, properties.rawRetention(), "原始访问日志查询范围不能超过 7 天");
        return new TimeWindow(from, to);
    }

    private TimeWindow hourlyWindow(Instant requestedFrom, Instant requestedTo) {
        Instant completedHour = Instant.now().truncatedTo(ChronoUnit.HOURS);
        Instant to = requestedTo == null
                ? completedHour
                : min(ceilHour(requestedTo), completedHour);
        Instant from = requestedFrom == null
                ? to.minus(24, ChronoUnit.HOURS)
                : requestedFrom.truncatedTo(ChronoUnit.HOURS);
        validateWindow(from, to, properties.hourlyRetention(), "小时统计查询范围不能超过 180 天");
        return new TimeWindow(from, to);
    }

    private static String rawWhere(
            MapSqlParameterSource parameters,
            UUID dataServiceId,
            UUID consumerId,
            Integer responseStatus,
            String gatewayRequestId,
            boolean abnormalOnly
    ) {
        StringBuilder where = new StringBuilder(
                "where l.occurred_at >= :from and l.occurred_at < :to"
        );
        if (dataServiceId != null) {
            where.append(" and l.data_service_id = :dataServiceId");
            parameters.addValue("dataServiceId", dataServiceId);
        }
        if (consumerId != null) {
            where.append(" and l.consumer_id = :consumerId");
            parameters.addValue("consumerId", consumerId);
        }
        if (responseStatus != null) {
            if (responseStatus < 100 || responseStatus > 599) {
                throw badRequest("responseStatus 必须是有效 HTTP 状态码");
            }
            where.append(" and l.response_status = :responseStatus");
            parameters.addValue("responseStatus", responseStatus);
        }
        if (gatewayRequestId != null && !gatewayRequestId.isBlank()) {
            where.append(" and l.gateway_request_id = :gatewayRequestId");
            parameters.addValue("gatewayRequestId", gatewayRequestId.trim());
        }
        if (abnormalOnly) {
            where.append("""
                     and (
                        l.response_status >= 400
                        or l.gateway_rejected
                        or l.gateway_error
                        or l.upstream_error
                     )
                    """);
        }
        return where.toString();
    }

    private static GatewayAccessLogResponse mapLog(ResultSet resultSet, int rowNumber) throws SQLException {
        return new GatewayAccessLogResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("event_id", UUID.class),
                resultSet.getString("schema_version"),
                GatewayProvider.valueOf(resultSet.getString("gateway_provider")),
                instant(resultSet, "occurred_at"),
                nullableInstant(resultSet, "observed_at"),
                instant(resultSet, "received_at"),
                resultSet.getObject("data_service_id", UUID.class),
                resultSet.getString("current_data_service_code"),
                resultSet.getString("current_data_service_name"),
                resultSet.getString("gateway_service_id"),
                resultSet.getString("gateway_service_name"),
                resultSet.getString("gateway_route_id"),
                resultSet.getString("gateway_route_name"),
                resultSet.getObject("consumer_id", UUID.class),
                resultSet.getString("gateway_consumer_id"),
                resultSet.getString("consumer_code"),
                resultSet.getString("current_consumer_name"),
                resultSet.getString("gateway_credential_external_id"),
                resultSet.getString("gateway_request_id"),
                resultSet.getString("request_method"),
                resultSet.getString("request_path"),
                resultSet.getInt("response_status"),
                resultSet.getString("upstream_status"),
                nullableLong(resultSet, "request_size_bytes"),
                nullableLong(resultSet, "response_size_bytes"),
                nullableLong(resultSet, "request_latency_ms"),
                nullableLong(resultSet, "kong_latency_ms"),
                nullableLong(resultSet, "proxy_latency_ms"),
                nullableLong(resultSet, "receive_latency_ms"),
                resultSet.getString("client_ip"),
                GatewayAccessIdentityResolutionStatus.valueOf(
                        resultSet.getString("identity_resolution_status")
                ),
                resultSet.getBoolean("gateway_rejected"),
                resultSet.getBoolean("gateway_error"),
                resultSet.getBoolean("upstream_error"),
                resultSet.getString("kafka_topic"),
                resultSet.getInt("kafka_partition"),
                resultSet.getLong("kafka_offset")
        );
    }

    private static GatewayAccessOverviewResponse mapOverview(
            ResultSet resultSet,
            TimeWindow window
    ) throws SQLException {
        long requestCount = resultSet.getLong("request_count");
        long requestLatencySamples = resultSet.getLong("request_latency_sample_count");
        long proxyLatencySamples = resultSet.getLong("proxy_latency_sample_count");
        long status2xx = resultSet.getLong("status_2xx_count");
        long status4xx = resultSet.getLong("status_4xx_count");
        long status5xx = resultSet.getLong("status_5xx_count");
        return new GatewayAccessOverviewResponse(
                window.fromInclusive(),
                window.toExclusive(),
                requestCount,
                status2xx,
                resultSet.getLong("status_3xx_count"),
                status4xx,
                status5xx,
                resultSet.getLong("status_401_count"),
                resultSet.getLong("status_403_count"),
                resultSet.getLong("status_429_count"),
                resultSet.getLong("gateway_rejected_count"),
                resultSet.getLong("gateway_error_count"),
                resultSet.getLong("upstream_error_count"),
                rate(status2xx, requestCount),
                rate(status4xx, requestCount),
                rate(status5xx, requestCount),
                resultSet.getLong("request_bytes_sum"),
                resultSet.getLong("response_bytes_sum"),
                average(resultSet.getLong("request_latency_sum_ms"), requestLatencySamples),
                nullableLong(resultSet, "request_latency_max_ms"),
                nullableDouble(resultSet, "request_latency_p95_ms"),
                nullableDouble(resultSet, "request_latency_p99_ms"),
                average(resultSet.getLong("proxy_latency_sum_ms"), proxyLatencySamples),
                nullableDouble(resultSet, "proxy_latency_p95_ms"),
                nullableDouble(resultSet, "proxy_latency_p99_ms")
        );
    }

    private static GatewayAccessTrendPointResponse mapTrendPoint(
            ResultSet resultSet,
            int rowNumber
    ) throws SQLException {
        long requestLatencySamples = resultSet.getLong("request_latency_sample_count");
        long proxyLatencySamples = resultSet.getLong("proxy_latency_sample_count");
        return new GatewayAccessTrendPointResponse(
                instant(resultSet, "hour_start"),
                resultSet.getLong("request_count"),
                resultSet.getLong("status_2xx_count"),
                resultSet.getLong("status_3xx_count"),
                resultSet.getLong("status_4xx_count"),
                resultSet.getLong("status_5xx_count"),
                resultSet.getLong("status_401_count"),
                resultSet.getLong("status_403_count"),
                resultSet.getLong("status_429_count"),
                resultSet.getLong("gateway_rejected_count"),
                resultSet.getLong("gateway_error_count"),
                resultSet.getLong("upstream_error_count"),
                resultSet.getLong("request_bytes_sum"),
                resultSet.getLong("response_bytes_sum"),
                average(resultSet.getLong("request_latency_sum_ms"), requestLatencySamples),
                nullableLong(resultSet, "request_latency_max_ms"),
                nullableDouble(resultSet, "request_latency_p95_ms"),
                nullableDouble(resultSet, "request_latency_p99_ms"),
                average(resultSet.getLong("proxy_latency_sum_ms"), proxyLatencySamples),
                nullableDouble(resultSet, "proxy_latency_p95_ms"),
                nullableDouble(resultSet, "proxy_latency_p99_ms")
        );
    }

    private static GatewayAccessHourlyStatResponse mapHourly(
            ResultSet resultSet,
            UUID consumerId
    ) throws SQLException {
        long requestSamples = resultSet.getLong("request_latency_sample_count");
        long proxySamples = resultSet.getLong("proxy_latency_sample_count");
        return new GatewayAccessHourlyStatResponse(
                instant(resultSet, "hour_start"),
                GatewayProvider.valueOf(resultSet.getString("gateway_provider")),
                resultSet.getObject("data_service_id", UUID.class),
                consumerId,
                resultSet.getLong("request_count"),
                resultSet.getLong("status_2xx_count"),
                resultSet.getLong("status_3xx_count"),
                resultSet.getLong("status_4xx_count"),
                resultSet.getLong("status_5xx_count"),
                resultSet.getLong("status_401_count"),
                resultSet.getLong("status_403_count"),
                resultSet.getLong("status_429_count"),
                resultSet.getLong("gateway_rejected_count"),
                resultSet.getLong("gateway_error_count"),
                resultSet.getLong("upstream_error_count"),
                resultSet.getLong("request_bytes_sum"),
                resultSet.getLong("response_bytes_sum"),
                average(resultSet.getLong("request_latency_sum_ms"), requestSamples),
                nullableLong(resultSet, "request_latency_max_ms"),
                nullableDouble(resultSet, "request_latency_p95_ms"),
                nullableDouble(resultSet, "request_latency_p99_ms"),
                average(resultSet.getLong("proxy_latency_sum_ms"), proxySamples),
                nullableDouble(resultSet, "proxy_latency_p95_ms"),
                nullableDouble(resultSet, "proxy_latency_p99_ms")
        );
    }

    private static MapSqlParameterSource hourlyParameters(TimeWindow window) {
        return new MapSqlParameterSource()
                .addValue("from", timestamp(window.fromInclusive()))
                .addValue("to", timestamp(window.toExclusive()));
    }

    private static void validateWindow(
            Instant from,
            Instant to,
            Duration maximum,
            String maximumMessage
    ) {
        if (from == null || to == null || !from.isBefore(to)) {
            throw badRequest("from 必须早于 to");
        }
        if (Duration.between(from, to).compareTo(maximum) > 0) {
            throw badRequest(maximumMessage);
        }
    }

    private static Instant ceilHour(Instant value) {
        Instant floor = value.truncatedTo(ChronoUnit.HOURS);
        return floor.equals(value) ? floor : floor.plus(1, ChronoUnit.HOURS);
    }

    private static Instant min(Instant left, Instant right) {
        return left.isBefore(right) ? left : right;
    }

    private static double rate(long value, long total) {
        return total == 0 ? 0D : (double) value / total;
    }

    private static Double average(long sum, long count) {
        return count == 0 ? null : (double) sum / count;
    }

    private static long requiredLong(Long value) {
        return value == null ? 0 : value;
    }

    private static Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Double nullableDouble(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        return resultSet.getTimestamp(column).toInstant();
    }

    private static Instant nullableInstant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static Timestamp timestamp(Instant value) {
        return Timestamp.from(value);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private record TimeWindow(Instant fromInclusive, Instant toExclusive) {
    }
}

package cn.superhuang.data.scalpel.business.service.accesslog.service;

import cn.superhuang.data.scalpel.business.service.accesslog.domain.GatewayAccessIdentityResolutionStatus;
import cn.superhuang.data.scalpel.business.service.gateway.GatewayProvider;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class GatewayAccessEventDecoder {

    private static final String SERVICE_PREFIX = "datascalpel-service-";
    private static final String ROUTE_PREFIX = "datascalpel-route-";
    private static final Pattern UPSTREAM_SERVER_ERROR = Pattern.compile("(^|\\D)5\\d\\d(\\D|$)");
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(10);

    private final ObjectMapper mapper;

    public GatewayAccessEventDecoder(ObjectMapper objectMapper) {
        this.mapper = objectMapper.rebuild()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
    }

    public GatewayAccessRecord decode(
            ConsumerRecord<String, String> source,
            Instant receivedAt,
            Duration rawRetention
    ) {
        GatewayAccessEvent event = mapper.readValue(source.value(), GatewayAccessEvent.class);
        requireEquals("1.0", event.schemaVersion(), "schema_version");
        requireEquals("gateway.access", event.eventType(), "event_type");

        UUID eventId = parseUuid(required(event.eventId(), "event_id"), "event_id");
        requireEquals(eventId.toString(), required(source.key(), "Kafka Key"), "Kafka Key");
        GatewayProvider provider = parseProvider(event.gatewayProvider());
        Instant occurredAt = parseOccurredAt(event.startedAtEpochMs());
        if (occurredAt.isBefore(receivedAt.minus(rawRetention))) {
            throw new IllegalArgumentException("事件已超过原始日志保留期");
        }
        if (occurredAt.isAfter(receivedAt.plus(MAX_FUTURE_SKEW))) {
            throw new IllegalArgumentException("事件发生时间超出允许的未来时钟偏差");
        }

        GatewayAccessObjectReference service = requireObject(event.service(), "service");
        String serviceId = limitedRequired(service.id(), "service.id", 200);
        String serviceName = limitedRequired(service.name(), "service.name", 200);
        UUID dataServiceId = parseManagedId(serviceName, SERVICE_PREFIX);

        String routeId = event.route() == null ? null : limited(event.route().id(), 200);
        String routeName = event.route() == null ? null : limited(event.route().name(), 200);
        UUID routeDataServiceId = routeName == null ? null : parseManagedId(routeName, ROUTE_PREFIX);

        String consumerCustomId = event.consumer() == null ? null : limited(event.consumer().customId(), 200);
        UUID consumerId = parseOptionalUuid(consumerCustomId);
        GatewayAccessIdentityResolutionStatus resolutionStatus = resolveIdentity(
                dataServiceId, routeName, routeDataServiceId, consumerCustomId, consumerId
        );

        GatewayAccessRequest request = requireObject(event.request(), "request");
        GatewayAccessResponse response = requireObject(event.response(), "response");
        int responseStatus = requireStatus(response.status());
        String upstreamStatus = limited(event.upstreamStatus(), 64);
        boolean hasUpstreamStatus = upstreamStatus != null;
        boolean gatewayRejected = !hasUpstreamStatus
                && (responseStatus == 401 || responseStatus == 403 || responseStatus == 429);
        boolean gatewayError = !hasUpstreamStatus && responseStatus >= 500;
        boolean upstreamError = hasUpstreamStatus
                && UPSTREAM_SERVER_ERROR.matcher(upstreamStatus).find();

        GatewayAccessLatencies latencies = event.latencies();
        return new GatewayAccessRecord(
                eventId,
                event.schemaVersion(),
                provider,
                occurredAt,
                parseObservedAt(event.observedAt()),
                receivedAt,
                dataServiceId,
                serviceId,
                serviceName,
                routeId,
                routeName,
                consumerId,
                event.consumer() == null ? null : limited(event.consumer().id(), 200),
                event.consumer() == null ? null : limited(event.consumer().username(), 64),
                event.credential() == null ? null : limited(event.credential().externalId(), 200),
                limitedRequired(request.id(), "request.id", 200),
                limitedRequired(request.method(), "request.method", 16).toUpperCase(Locale.ROOT),
                normalizedPath(request.path()),
                responseStatus,
                upstreamStatus,
                nonNegative(request.sizeBytes()),
                nonNegative(response.sizeBytes()),
                latencies == null ? null : nonNegative(latencies.request()),
                latencies == null ? null : nonNegative(latencies.kong()),
                latencies == null ? null : nonNegative(latencies.proxy()),
                latencies == null ? null : nonNegative(latencies.receive()),
                limited(event.clientIp(), 64),
                resolutionStatus,
                gatewayRejected,
                gatewayError,
                upstreamError,
                source.topic(),
                source.partition(),
                source.offset()
        );
    }

    private static GatewayAccessIdentityResolutionStatus resolveIdentity(
            UUID dataServiceId,
            String routeName,
            UUID routeDataServiceId,
            String consumerCustomId,
            UUID consumerId
    ) {
        if (dataServiceId == null) {
            return GatewayAccessIdentityResolutionStatus.SERVICE_UNRESOLVED;
        }
        if (routeName != null && (routeDataServiceId == null || !dataServiceId.equals(routeDataServiceId))) {
            return GatewayAccessIdentityResolutionStatus.IDENTITY_MISMATCH;
        }
        if (consumerCustomId == null) {
            return GatewayAccessIdentityResolutionStatus.ANONYMOUS;
        }
        if (consumerId == null) {
            return GatewayAccessIdentityResolutionStatus.CONSUMER_UNRESOLVED;
        }
        return GatewayAccessIdentityResolutionStatus.RESOLVED;
    }

    private static GatewayProvider parseProvider(String value) {
        String normalized = required(value, "gateway_provider").toUpperCase(Locale.ROOT);
        try {
            GatewayProvider provider = GatewayProvider.valueOf(normalized);
            if (provider == GatewayProvider.NONE) {
                throw new IllegalArgumentException("gateway_provider 不支持 NONE");
            }
            return provider;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("gateway_provider 不受支持");
        }
    }

    private static Instant parseOccurredAt(Long epochMilliseconds) {
        if (epochMilliseconds == null) {
            throw new IllegalArgumentException("started_at_epoch_ms 不能为空");
        }
        try {
            return Instant.ofEpochMilli(epochMilliseconds);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("started_at_epoch_ms 无效");
        }
    }

    private static Instant parseObservedAt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value.trim());
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("observed_at 无效");
        }
    }

    private static UUID parseManagedId(String value, String prefix) {
        if (value == null || !value.startsWith(prefix)) {
            return null;
        }
        return parseOptionalUuid(value.substring(prefix.length()));
    }

    private static UUID parseOptionalUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static UUID parseUuid(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(field + " 必须是 UUID");
        }
    }

    private static int requireStatus(Integer value) {
        if (value == null || value < 100 || value > 599) {
            throw new IllegalArgumentException("response.status 必须是有效 HTTP 状态码");
        }
        return value;
    }

    private static Long nonNegative(Long value) {
        return value == null || value < 0 ? null : value;
    }

    private static String normalizedPath(String value) {
        String path = required(value, "request.path");
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        if (path.isBlank()) {
            path = "/";
        }
        return path.substring(0, Math.min(2048, path.length()));
    }

    private static String limitedRequired(String value, String field, int limit) {
        String normalized = required(value, field);
        return normalized.substring(0, Math.min(limit, normalized.length()));
    }

    private static String limited(String value, int limit) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.substring(0, Math.min(limit, normalized.length()));
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value.trim();
    }

    private static <T> T requireObject(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " 不能为空");
        }
        return value;
    }

    private static void requireEquals(String expected, String actual, String field) {
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(field + " 不受支持");
        }
    }
}

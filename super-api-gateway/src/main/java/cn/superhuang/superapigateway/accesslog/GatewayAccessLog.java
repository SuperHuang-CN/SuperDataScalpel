package cn.superhuang.superapigateway.accesslog;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record GatewayAccessLog(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("observed_at") Instant observedAt,
        @JsonProperty("gateway_provider") String gatewayProvider,
        @JsonProperty("started_at_epoch_ms") long startedAtEpochMs,
        @JsonProperty("instance_id") String instanceId,
        Reference service,
        Reference route,
        Consumer consumer,
        Request request,
        Response response,
        @JsonProperty("latencies_ms") Latencies latencies,
        @JsonProperty("client_ip") String clientIp,
        @JsonProperty("upstream_status") String upstreamStatus
) {
    public record Reference(String id, String name, @JsonProperty("external_id") String externalId) {}
    public record Consumer(String id, String username, @JsonProperty("custom_id") String customId) {}
    public record Request(String id, String method, String path,
                          @JsonProperty("size_bytes") Long sizeBytes) {}
    public record Response(int status, @JsonProperty("size_bytes") Long sizeBytes) {}
    public record Latencies(long request, Long gateway, Long proxy, Long receive) {}
}

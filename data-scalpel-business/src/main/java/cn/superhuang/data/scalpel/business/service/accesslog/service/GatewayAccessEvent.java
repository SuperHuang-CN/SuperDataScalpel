package cn.superhuang.data.scalpel.business.service.accesslog.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
record GatewayAccessEvent(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("event_type") String eventType,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("observed_at") String observedAt,
        @JsonProperty("gateway_provider") String gatewayProvider,
        @JsonProperty("started_at_epoch_ms") Long startedAtEpochMs,
        GatewayAccessObjectReference service,
        GatewayAccessObjectReference route,
        GatewayAccessConsumerReference consumer,
        GatewayAccessCredentialReference credential,
        GatewayAccessRequest request,
        GatewayAccessResponse response,
        @JsonProperty("latencies_ms") GatewayAccessLatencies latencies,
        @JsonProperty("client_ip") String clientIp,
        @JsonProperty("upstream_status") String upstreamStatus
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record GatewayAccessObjectReference(String id, String name) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record GatewayAccessConsumerReference(
        String id,
        @JsonProperty("custom_id") String customId,
        String username
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record GatewayAccessCredentialReference(
        @JsonProperty("external_id") String externalId
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record GatewayAccessRequest(
        String id,
        String method,
        String path,
        @JsonProperty("size_bytes") Long sizeBytes
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record GatewayAccessResponse(
        Integer status,
        @JsonProperty("size_bytes") Long sizeBytes
) {
}

@JsonIgnoreProperties(ignoreUnknown = true)
record GatewayAccessLatencies(
        Long request,
        Long kong,
        Long proxy,
        Long receive
) {
}

package cn.superhuang.data.scalpel.business.service.gateway.datascalpel;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

final class SuperApiGatewayModels {

    private SuperApiGatewayModels() {
    }

    record PageResponse<T>(
            List<T> content,
            int page,
            int size,
            long totalElements,
            int totalPages
    ) {
    }

    record ServiceResponse(
            UUID id,
            String code,
            String name,
            String upstreamUri,
            String accessMode,
            int connectTimeoutMs,
            int responseTimeoutMs,
            boolean enabled,
            String description,
            String source,
            String externalId,
            long revision,
            long routeCount,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record RouteResponse(
            UUID id,
            UUID serviceId,
            String code,
            String name,
            String pathPattern,
            Set<String> methods,
            int order,
            int stripPrefixSegments,
            String upstreamPath,
            boolean enabled,
            String source,
            String externalId,
            long revision,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record ConsumerResponse(
            UUID id,
            String code,
            String name,
            boolean enabled,
            String description,
            String source,
            String externalId,
            long revision,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record ApiKeyResponse(
            UUID id,
            UUID consumerId,
            String name,
            String prefix,
            String lastFour,
            String status,
            Instant rotatedAt,
            String source,
            String externalId,
            String secret,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record ApiKeyDetailResponse(
            UUID id,
            UUID consumerId,
            String name,
            String prefix,
            String lastFour,
            String status,
            Instant rotatedAt,
            String source,
            String externalId,
            String secretDigest,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record SubscriptionResponse(
            UUID id,
            UUID consumerId,
            UUID serviceId,
            String status,
            String source,
            String externalId,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    record CreateServiceRequest(
            String code,
            String name,
            String upstreamUri,
            String accessMode,
            int connectTimeoutMs,
            int responseTimeoutMs,
            boolean enabled,
            String description,
            String source,
            String externalId
    ) {
    }

    record UpdateServiceRequest(
            String name,
            String upstreamUri,
            String accessMode,
            int connectTimeoutMs,
            int responseTimeoutMs,
            boolean enabled,
            String description
    ) {
    }

    record CreateRouteRequest(
            UUID serviceId,
            String code,
            String name,
            String pathPattern,
            Set<String> methods,
            int order,
            int stripPrefixSegments,
            String upstreamPath,
            boolean enabled,
            String source,
            String externalId
    ) {
    }

    record UpdateRouteRequest(
            String name,
            String pathPattern,
            Set<String> methods,
            int order,
            int stripPrefixSegments,
            String upstreamPath,
            boolean enabled
    ) {
    }

    record CreateConsumerRequest(
            String code,
            String name,
            boolean enabled,
            String description,
            String source,
            String externalId
    ) {
    }

    record UpdateConsumerRequest(
            String name,
            boolean enabled,
            String description
    ) {
    }

    record CreateApiKeyRequest(
            String name,
            String source,
            String externalId,
            String secret
    ) {
    }

    record RotateApiKeyRequest(String secret) {
    }

    record GrantSubscriptionRequest(
            UUID consumerId,
            UUID serviceId,
            String source,
            String externalId
    ) {
    }

    record ProblemResponse(
            String title,
            Integer status,
            String detail,
            String code
    ) {
    }
}

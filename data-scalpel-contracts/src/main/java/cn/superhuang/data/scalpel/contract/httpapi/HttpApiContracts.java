package cn.superhuang.data.scalpel.contract.httpapi;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Stable management/runner contracts for read-only HTTP/JSON API ingestion. */
public final class HttpApiContracts {

    public static final String GENERIC_CONNECTOR = "GENERIC_HTTP";

    private HttpApiContracts() {
    }

    public enum HttpMethod {
        GET,
        POST
    }

    public enum ValueLocation {
        HEADER,
        QUERY,
        BODY
    }

    public enum SignatureType {
        NONE,
        MD5,
        HMAC_SHA256,
        HMAC_SHA512,
        RSA_SHA256
    }

    public enum SignatureEncoding {
        HEX_LOWERCASE,
        HEX_UPPERCASE,
        BASE64,
        BASE64_URL
    }

    public enum TimestampUnit {
        SECONDS,
        MILLISECONDS
    }

    public enum InvocationType {
        SINGLE_REQUEST,
        PAGINATED_REQUEST,
        ASYNC_JOB
    }

    public enum PaginationType {
        NONE,
        PAGE_NUMBER,
        OFFSET_LIMIT,
        CURSOR,
        NEXT_URL
    }

    public record NamedValue(String name, String value) {
    }

    public record RuntimeParameter(String name, String value) {
    }

    public record RequestTemplate(
            HttpMethod method,
            String path,
            List<NamedValue> queryParameters,
            List<NamedValue> headers,
            String bodyTemplate
    ) {
        public RequestTemplate {
            queryParameters = immutable(queryParameters);
            headers = immutable(headers);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = NoneAuthentication.class, name = "NONE"),
            @JsonSubTypes.Type(value = BasicAuthentication.class, name = "BASIC"),
            @JsonSubTypes.Type(value = BearerTokenAuthentication.class, name = "BEARER_TOKEN"),
            @JsonSubTypes.Type(value = ApiKeyAuthentication.class, name = "API_KEY"),
            @JsonSubTypes.Type(value = OAuth2ClientCredentialsAuthentication.class, name = "OAUTH2_CLIENT_CREDENTIALS"),
            @JsonSubTypes.Type(value = TokenEndpointAuthentication.class, name = "TOKEN_ENDPOINT")
    })
    public sealed interface AuthenticationConfiguration permits NoneAuthentication, BasicAuthentication,
            BearerTokenAuthentication, ApiKeyAuthentication, OAuth2ClientCredentialsAuthentication,
            TokenEndpointAuthentication {

        String type();
    }

    public record NoneAuthentication() implements AuthenticationConfiguration {
        @Override
        public String type() {
            return "NONE";
        }
    }

    public record BasicAuthentication(
            String username,
            boolean passwordConfigured
    ) implements AuthenticationConfiguration {
        @Override
        public String type() {
            return "BASIC";
        }
    }

    public record BearerTokenAuthentication(
            boolean tokenConfigured
    ) implements AuthenticationConfiguration {
        @Override
        public String type() {
            return "BEARER_TOKEN";
        }
    }

    public record ApiKeyAuthentication(
            ValueLocation location,
            String name,
            String valueTemplate,
            boolean apiKeyConfigured
    ) implements AuthenticationConfiguration {
        @Override
        public String type() {
            return "API_KEY";
        }
    }

    public record OAuth2ClientCredentialsAuthentication(
            String tokenUrl,
            String clientId,
            List<String> scopes,
            String audience,
            ValueLocation tokenLocation,
            String tokenName,
            String tokenValueTemplate,
            boolean clientSecretConfigured
    ) implements AuthenticationConfiguration {
        public OAuth2ClientCredentialsAuthentication {
            scopes = immutable(scopes);
        }

        @Override
        public String type() {
            return "OAUTH2_CLIENT_CREDENTIALS";
        }
    }

    public record TokenEndpointAuthentication(
            String tokenUrl,
            HttpMethod method,
            List<NamedValue> headers,
            String bodyTemplate,
            String username,
            String tokenPointer,
            String expiresInPointer,
            Integer fixedTtlSeconds,
            ValueLocation tokenLocation,
            String tokenName,
            String tokenValueTemplate,
            boolean passwordConfigured
    ) implements AuthenticationConfiguration {
        public TokenEndpointAuthentication {
            headers = immutable(headers);
        }

        @Override
        public String type() {
            return "TOKEN_ENDPOINT";
        }
    }

    /** Write-only values. Instances may enter an execution manifest but must never enter a Web response. */
    public record CredentialBundle(
            String password,
            String bearerToken,
            String apiKey,
            String clientSecret,
            String tokenEndpointPassword,
            String signingSecret,
            String signingPrivateKey
    ) {
    }

    public record ConnectionConfiguration(
            String baseUrl,
            List<NamedValue> defaultHeaders,
            int connectTimeoutMs,
            int requestTimeoutMs,
            int minimumRequestIntervalMs,
            int maxRetries,
            AuthenticationConfiguration authentication,
            boolean signingSecretConfigured,
            boolean signingPrivateKeyConfigured
    ) {
        public ConnectionConfiguration {
            defaultHeaders = immutable(defaultHeaders);
        }
    }

    public record RuntimeConnection(
            ConnectionConfiguration configuration,
            CredentialBundle credentials
    ) {
    }

    public record TimestampConfiguration(
            String name,
            ValueLocation location,
            TimestampUnit unit
    ) {
    }

    public record NonceConfiguration(
            String name,
            ValueLocation location
    ) {
    }

    public record SignatureOutput(
            String name,
            ValueLocation location,
            SignatureEncoding encoding,
            String valueTemplate
    ) {
    }

    public record SigningConfiguration(
            SignatureType type,
            String canonicalTemplate,
            TimestampConfiguration timestamp,
            NonceConfiguration nonce,
            SignatureOutput output
    ) {
        public static SigningConfiguration none() {
            return new SigningConfiguration(SignatureType.NONE, null, null, null, null);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = NoPagination.class, name = "NONE"),
            @JsonSubTypes.Type(value = PageNumberPagination.class, name = "PAGE_NUMBER"),
            @JsonSubTypes.Type(value = OffsetLimitPagination.class, name = "OFFSET_LIMIT"),
            @JsonSubTypes.Type(value = CursorPagination.class, name = "CURSOR"),
            @JsonSubTypes.Type(value = NextUrlPagination.class, name = "NEXT_URL")
    })
    public sealed interface PaginationConfiguration permits NoPagination, PageNumberPagination,
            OffsetLimitPagination, CursorPagination, NextUrlPagination {

        PaginationType type();
    }

    public record NoPagination() implements PaginationConfiguration {
        @Override
        public PaginationType type() {
            return PaginationType.NONE;
        }
    }

    public record PageNumberPagination(
            ValueLocation location,
            String pageParameter,
            String pageSizeParameter,
            int initialPage,
            int pageSize,
            String hasMorePointer,
            String totalPagesPointer
    ) implements PaginationConfiguration {
        @Override
        public PaginationType type() {
            return PaginationType.PAGE_NUMBER;
        }
    }

    public record OffsetLimitPagination(
            ValueLocation location,
            String offsetParameter,
            String limitParameter,
            int initialOffset,
            int limit,
            String hasMorePointer,
            String totalPointer
    ) implements PaginationConfiguration {
        @Override
        public PaginationType type() {
            return PaginationType.OFFSET_LIMIT;
        }
    }

    public record CursorPagination(
            ValueLocation location,
            String cursorParameter,
            String initialCursor,
            String nextCursorPointer,
            String hasMorePointer
    ) implements PaginationConfiguration {
        @Override
        public PaginationType type() {
            return PaginationType.CURSOR;
        }
    }

    public record NextUrlPagination(
            String nextUrlPointer,
            boolean sameOriginOnly
    ) implements PaginationConfiguration {
        @Override
        public PaginationType type() {
            return PaginationType.NEXT_URL;
        }
    }

    public record AsyncJobConfiguration(
            RequestTemplate statusRequest,
            String jobIdPointer,
            String statusPointer,
            Set<String> runningStatuses,
            Set<String> successStatuses,
            Set<String> failureStatuses,
            int pollingIntervalMs,
            int pollingTimeoutMs,
            RequestTemplate resultRequest
    ) {
        public AsyncJobConfiguration {
            runningStatuses = immutable(runningStatuses);
            successStatuses = immutable(successStatuses);
            failureStatuses = immutable(failureStatuses);
        }
    }

    public record ExecutionLimits(
            int maxPages,
            long maxRows,
            long maxResponseBytes,
            int maxDurationSeconds
    ) {
    }

    public record OutputField(
            String name,
            String jsonPointer,
            PlatformTypeDefinition type,
            boolean nullable,
            String comment
    ) {
    }

    public record ResourceDefinition(
            UUID id,
            UUID dataSourceId,
            String code,
            String name,
            String connectorType,
            boolean enabled,
            RequestTemplate request,
            SigningConfiguration signing,
            InvocationType invocationType,
            PaginationConfiguration pagination,
            AsyncJobConfiguration asyncJob,
            String recordsPointer,
            List<OutputField> outputFields,
            ExecutionLimits limits
    ) {
        public ResourceDefinition {
            outputFields = immutable(outputFields);
        }
    }

    public record PullRequest(
            RuntimeConnection connection,
            ResourceDefinition resource,
            List<RuntimeParameter> runtimeParameters
    ) {
        public PullRequest {
            runtimeParameters = immutable(runtimeParameters);
        }
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static <T> Set<T> immutable(Set<T> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }
}

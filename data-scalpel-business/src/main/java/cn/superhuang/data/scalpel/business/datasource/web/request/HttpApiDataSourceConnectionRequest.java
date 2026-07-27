package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record HttpApiDataSourceConnectionRequest(
        @NotBlank @Size(max = 500) String baseUrl,
        @Size(max = 50) List<@Valid HeaderRequest> defaultHeaders,
        @Min(100) @Max(120_000) Integer connectTimeoutMs,
        @Min(100) @Max(600_000) Integer requestTimeoutMs,
        @Min(0) @Max(60_000) Integer minimumRequestIntervalMs,
        @Min(0) @Max(5) Integer maxRetries,
        @NotNull @Valid AuthenticationRequest authentication,
        @Size(max = 16_384) String signingSecret,
        @Size(max = 32_768) String signingPrivateKey
) implements DataSourceConnectionRequest {

    @Override
    public DataSourceConnectionKind kind() {
        return DataSourceConnectionKind.HTTP_API;
    }

    public record HeaderRequest(
            @NotBlank @Size(max = 128) String name,
            @NotBlank @Size(max = 2000) String value
    ) {
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = NoneAuthenticationRequest.class, name = "NONE"),
            @JsonSubTypes.Type(value = BasicAuthenticationRequest.class, name = "BASIC"),
            @JsonSubTypes.Type(value = BearerAuthenticationRequest.class, name = "BEARER_TOKEN"),
            @JsonSubTypes.Type(value = ApiKeyAuthenticationRequest.class, name = "API_KEY"),
            @JsonSubTypes.Type(value = OAuth2AuthenticationRequest.class, name = "OAUTH2_CLIENT_CREDENTIALS"),
            @JsonSubTypes.Type(value = TokenEndpointAuthenticationRequest.class, name = "TOKEN_ENDPOINT")
    })
    public sealed interface AuthenticationRequest permits NoneAuthenticationRequest, BasicAuthenticationRequest,
            BearerAuthenticationRequest, ApiKeyAuthenticationRequest, OAuth2AuthenticationRequest,
            TokenEndpointAuthenticationRequest {
    }

    public record NoneAuthenticationRequest() implements AuthenticationRequest {
    }

    public record BasicAuthenticationRequest(
            @NotBlank @Size(max = 256) String username,
            @Size(max = 16_384) String password
    ) implements AuthenticationRequest {
    }

    public record BearerAuthenticationRequest(
            @Size(max = 16_384) String token
    ) implements AuthenticationRequest {
    }

    public record ApiKeyAuthenticationRequest(
            @NotNull HttpApiContracts.ValueLocation location,
            @NotBlank @Size(max = 128) String name,
            @Size(max = 512) String valueTemplate,
            @Size(max = 16_384) String apiKey
    ) implements AuthenticationRequest {
    }

    public record OAuth2AuthenticationRequest(
            @NotBlank @Size(max = 1000) String tokenUrl,
            @NotBlank @Size(max = 512) String clientId,
            @Size(max = 50) List<@Size(max = 256) String> scopes,
            @Size(max = 512) String audience,
            HttpApiContracts.ValueLocation tokenLocation,
            @Size(max = 128) String tokenName,
            @Size(max = 512) String tokenValueTemplate,
            @Size(max = 16_384) String clientSecret
    ) implements AuthenticationRequest {
    }

    public record TokenEndpointAuthenticationRequest(
            @NotBlank @Size(max = 1000) String tokenUrl,
            @NotNull HttpApiContracts.HttpMethod method,
            @Size(max = 50) List<@Valid HeaderRequest> headers,
            @Size(max = 16_384) String bodyTemplate,
            @Size(max = 256) String username,
            @NotBlank @Size(max = 1000) String tokenPointer,
            @Size(max = 1000) String expiresInPointer,
            @Min(30) @Max(86_400) Integer fixedTtlSeconds,
            HttpApiContracts.ValueLocation tokenLocation,
            @Size(max = 128) String tokenName,
            @Size(max = 512) String tokenValueTemplate,
            @Size(max = 16_384) String password
    ) implements AuthenticationRequest {
    }
}

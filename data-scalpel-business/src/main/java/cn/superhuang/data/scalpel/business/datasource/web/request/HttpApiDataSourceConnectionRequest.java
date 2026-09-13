package cn.superhuang.data.scalpel.business.datasource.web.request;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

@Schema(description = "HTTP/JSON API 的公共连接、限流与认证配置；资源级请求路径、分页和结果字段另行配置")
public record HttpApiDataSourceConnectionRequest(
        @Schema(description = "远端 API 基地址；资源请求的相对 path 会基于此地址解析", example = "https://api.example.internal/v1/")
        @NotBlank @Size(max = 500) String baseUrl,
        @Schema(description = "每次请求都携带的非敏感默认请求头；资源请求头可覆盖同名项")
        @Size(max = 50) List<@Valid HeaderRequest> defaultHeaders,
        @Schema(description = "建立 TCP/TLS 连接的超时时间，单位毫秒；省略时为 5000")
        @Min(100) @Max(120_000) Integer connectTimeoutMs,
        @Schema(description = "单次 HTTP 请求的总超时时间，单位毫秒；省略时为 30000")
        @Min(100) @Max(600_000) Integer requestTimeoutMs,
        @Schema(description = "同一资源相邻请求之间的最小间隔，单位毫秒；用于尊重远端限流，省略或 0 表示不额外等待")
        @Min(0) @Max(60_000) Integer minimumRequestIntervalMs,
        @Schema(description = "可重试网络错误的最大重试次数；省略时为 2，0 表示不重试。业务写接口不应配置为数据源")
        @Min(0) @Max(5) Integer maxRetries,
        @Schema(description = "认证方式联合类型；type 决定所需字段，敏感值只写不返回")
        @NotNull @Valid AuthenticationRequest authentication,
        @Schema(description = "HMAC/MD5 等签名使用的共享密钥，只写不返回；更新传 null 保留原值", accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 16_384) String signingSecret,
        @Schema(description = "RSA 签名私钥，只写不返回；更新传 null 保留原值", accessMode = Schema.AccessMode.WRITE_ONLY)
        @Size(max = 32_768) String signingPrivateKey
) implements DataSourceConnectionRequest {

    @Override
    public DataSourceConnectionKind kind() {
        return DataSourceConnectionKind.HTTP_API;
    }

    @Schema(description = "HTTP 请求头名称和值；值可包含资源运行时支持的模板变量")
    public record HeaderRequest(
            @Schema(description = "请求头名称", example = "Accept-Language")
            @NotBlank @Size(max = 128) String name,
            @Schema(description = "请求头值或模板", example = "zh-CN")
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
    @Schema(
            description = "HTTP API 认证配置；type 可为 NONE、BASIC、BEARER_TOKEN、API_KEY、OAUTH2_CLIENT_CREDENTIALS 或 TOKEN_ENDPOINT",
            discriminatorProperty = "type"
    )
    public sealed interface AuthenticationRequest permits NoneAuthenticationRequest, BasicAuthenticationRequest,
            BearerAuthenticationRequest, ApiKeyAuthenticationRequest, OAuth2AuthenticationRequest,
            TokenEndpointAuthenticationRequest {
    }

    @Schema(description = "不向远端 API 添加认证信息，type 固定为 NONE")
    public record NoneAuthenticationRequest() implements AuthenticationRequest {
    }

    @Schema(description = "HTTP Basic 认证，type 固定为 BASIC")
    public record BasicAuthenticationRequest(
            @Schema(description = "Basic 认证用户名")
            @NotBlank @Size(max = 256) String username,
            @Schema(description = "Basic 认证密码，只写不返回；更新传 null 保留原密码", accessMode = Schema.AccessMode.WRITE_ONLY)
            @Size(max = 16_384) String password
    ) implements AuthenticationRequest {
    }

    @Schema(description = "固定 Bearer Token 认证，type 固定为 BEARER_TOKEN")
    public record BearerAuthenticationRequest(
            @Schema(description = "完整 Bearer Token，只写不返回；不要包含 Bearer 前缀，更新传 null 保留原 Token", accessMode = Schema.AccessMode.WRITE_ONLY)
            @Size(max = 16_384) String token
    ) implements AuthenticationRequest {
    }

    @Schema(description = "固定 API Key 认证，type 固定为 API_KEY")
    public record ApiKeyAuthenticationRequest(
            @Schema(description = "API Key 注入位置；通常为 HEADER 或 QUERY")
            @NotNull HttpApiContracts.ValueLocation location,
            @Schema(description = "承载 API Key 的请求头或查询参数名称", example = "X-API-Key")
            @NotBlank @Size(max = 128) String name,
            @Schema(description = "API Key 最终值模板；使用密钥占位符组合厂商要求的前后缀")
            @Size(max = 512) String valueTemplate,
            @Schema(description = "API Key 原始密钥，只写不返回；更新传 null 保留原密钥", accessMode = Schema.AccessMode.WRITE_ONLY)
            @Size(max = 16_384) String apiKey
    ) implements AuthenticationRequest {
    }

    @Schema(description = "OAuth2 Client Credentials 认证，type 固定为 OAUTH2_CLIENT_CREDENTIALS")
    public record OAuth2AuthenticationRequest(
            @Schema(description = "OAuth2 Token Endpoint 完整地址")
            @NotBlank @Size(max = 1000) String tokenUrl,
            @Schema(description = "OAuth2 Client ID")
            @NotBlank @Size(max = 512) String clientId,
            @Schema(description = "请求访问令牌时携带的 OAuth2 scope 列表；空列表表示不发送 scope 参数")
            @Size(max = 50) List<@Size(max = 256) String> scopes,
            @Schema(description = "可选 audience 参数，按远端身份服务要求填写")
            @Size(max = 512) String audience,
            @Schema(description = "获取到的访问令牌注入业务请求的位置；省略时使用 Bearer Authorization 头")
            HttpApiContracts.ValueLocation tokenLocation,
            @Schema(description = "非默认令牌注入位置使用的请求头或查询参数名称")
            @Size(max = 128) String tokenName,
            @Schema(description = "令牌最终值模板；省略时 Header 场景使用 Bearer 前缀")
            @Size(max = 512) String tokenValueTemplate,
            @Schema(description = "OAuth2 Client Secret，只写不返回；更新传 null 保留原密钥", accessMode = Schema.AccessMode.WRITE_ONLY)
            @Size(max = 16_384) String clientSecret
    ) implements AuthenticationRequest {
    }

    @Schema(description = "调用自定义 Token Endpoint 获取短期令牌，type 固定为 TOKEN_ENDPOINT")
    public record TokenEndpointAuthenticationRequest(
            @Schema(description = "Token Endpoint 完整地址")
            @NotBlank @Size(max = 1000) String tokenUrl,
            @Schema(description = "调用 Token Endpoint 使用的 HTTP 方法")
            @NotNull HttpApiContracts.HttpMethod method,
            @Schema(description = "Token Endpoint 专用请求头")
            @Size(max = 50) List<@Valid HeaderRequest> headers,
            @Schema(description = "Token Endpoint 请求体模板；通常为 JSON 或表单文本，密码通过受控模板变量引用")
            @Size(max = 16_384) String bodyTemplate,
            @Schema(description = "Token Endpoint 用户名；是否需要由远端协议决定")
            @Size(max = 256) String username,
            @Schema(description = "从 Token Endpoint JSON 响应提取访问令牌的 JSON Pointer", example = "/access_token")
            @NotBlank @Size(max = 1000) String tokenPointer,
            @Schema(description = "从响应中提取 expires_in 有效秒数的 JSON Pointer；无法提供时使用 fixedTtlSeconds")
            @Size(max = 1000) String expiresInPointer,
            @Schema(description = "无法从响应读取有效期时采用的固定令牌 TTL，单位秒")
            @Min(30) @Max(86_400) Integer fixedTtlSeconds,
            @Schema(description = "获取到的令牌注入业务请求的位置；省略时使用 Bearer Authorization 头")
            HttpApiContracts.ValueLocation tokenLocation,
            @Schema(description = "非默认令牌注入位置使用的请求头或查询参数名称")
            @Size(max = 128) String tokenName,
            @Schema(description = "令牌最终值模板；省略时 Header 场景使用 Bearer 前缀")
            @Size(max = 512) String tokenValueTemplate,
            @Schema(description = "Token Endpoint 密码，只写不返回；更新传 null 保留原密码", accessMode = Schema.AccessMode.WRITE_ONLY)
            @Size(max = 16_384) String password
    ) implements AuthenticationRequest {
    }
}

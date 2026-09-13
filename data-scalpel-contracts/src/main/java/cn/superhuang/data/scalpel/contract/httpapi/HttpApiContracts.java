package cn.superhuang.data.scalpel.contract.httpapi;

import cn.superhuang.data.scalpel.contract.type.PlatformTypeDefinition;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Stable management/runner contracts for read-only HTTP/JSON API ingestion. */
public final class HttpApiContracts {

    public static final String GENERIC_CONNECTOR = "GENERIC_HTTP";

    private HttpApiContracts() {
    }

    @JsonClassDescription("远端 HTTP 请求方法；当前只允许 GET 和 POST，POST 仍必须用于读取或异步查询场景。")
    public enum HttpMethod {
        GET,
        POST
    }

    @JsonClassDescription("生成值或分页参数的注入位置：请求头、查询参数或 JSON 请求体。")
    public enum ValueLocation {
        HEADER,
        QUERY,
        BODY
    }

    @JsonClassDescription("远端请求签名算法；NONE 不签名，其他算法需要数据源配置共享密钥或 RSA 私钥。")
    public enum SignatureType {
        NONE,
        MD5,
        HMAC_SHA256,
        HMAC_SHA512,
        RSA_SHA256
    }

    @JsonClassDescription("签名二进制结果的文本编码方式。")
    public enum SignatureEncoding {
        HEX_LOWERCASE,
        HEX_UPPERCASE,
        BASE64,
        BASE64_URL
    }

    @JsonClassDescription("签名时间戳单位：秒或毫秒。")
    public enum TimestampUnit {
        SECONDS,
        MILLISECONDS
    }

    @JsonClassDescription("资源调用模式：单次请求、分页拉取，或提交远端异步任务后轮询并读取结果。")
    public enum InvocationType {
        SINGLE_REQUEST,
        PAGINATED_REQUEST,
        ASYNC_JOB
    }

    @JsonClassDescription("结果分页策略：不分页、页码、偏移量、游标或响应给出的下一页 URL。")
    public enum PaginationType {
        NONE,
        PAGE_NUMBER,
        OFFSET_LIMIT,
        CURSOR,
        NEXT_URL
    }

    @JsonClassDescription("一个实际发送的请求头或查询参数名称及其受控模板值。")
    public record NamedValue(
            @JsonPropertyDescription("名称；在查询参数和请求头中表示实际发送的键") String name,
            @JsonPropertyDescription("值或模板；模板可引用运行时参数和受控凭据变量") String value
    ) {
    }

    @JsonClassDescription("调用 HTTP API 资源时临时提供的模板参数；只对本次拉取生效。")
    public record RuntimeParameter(
            @JsonPropertyDescription("运行时参数名称，必须与资源模板中的引用一致") String name,
            @JsonPropertyDescription("本次调用使用的参数值；不会写回资源定义") String value
    ) {
    }

    @JsonClassDescription("只读 HTTP API 请求模板；限定方法、相对路径、查询参数、请求头和可选请求体。")
    public record RequestTemplate(
            @JsonPropertyDescription("请求方法；数据源只支持读取场景下的 GET 或 POST")
            HttpMethod method,
            @JsonPropertyDescription("相对于连接 baseUrl 的请求路径模板；不能改变到其他主机")
            String path,
            @JsonPropertyDescription("查询参数模板列表；同一列表中不允许重名")
            List<NamedValue> queryParameters,
            @JsonPropertyDescription("资源级请求头模板；同名项覆盖连接默认请求头")
            List<NamedValue> headers,
            @JsonPropertyDescription("可选请求体模板；通常用于只读 POST 查询")
            String bodyTemplate
    ) {
        public RequestTemplate {
            queryParameters = immutable(queryParameters);
            headers = immutable(headers);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonClassDescription("远端 HTTP API 的非敏感认证配置视图；type 决定具体字段，秘密仅以是否已配置表示。")
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

    @JsonClassDescription("不向远端 HTTP API 注入任何认证信息。")
    public record NoneAuthentication() implements AuthenticationConfiguration {
        @Override
        public String type() {
            return "NONE";
        }
    }

    @JsonClassDescription("HTTP Basic 认证的公开配置；只返回用户名和密码是否已保存。")
    public record BasicAuthentication(
            @JsonPropertyDescription("Basic 认证用户名")
            String username,
            @JsonPropertyDescription("是否已保存 Basic 密码；响应不返回密码本身")
            boolean passwordConfigured
    ) implements AuthenticationConfiguration {
        @Override
        public String type() {
            return "BASIC";
        }
    }

    @JsonClassDescription("固定 Bearer Token 认证的公开配置；不返回 Token 明文。")
    public record BearerTokenAuthentication(
            @JsonPropertyDescription("是否已保存固定 Bearer Token；响应不返回 Token 本身")
            boolean tokenConfigured
    ) implements AuthenticationConfiguration {
        @Override
        public String type() {
            return "BEARER_TOKEN";
        }
    }

    @JsonClassDescription("固定 API Key 的注入位置、字段名、值模板和密钥配置状态。")
    public record ApiKeyAuthentication(
            @JsonPropertyDescription("API Key 注入位置：请求头、查询参数或请求体")
            ValueLocation location,
            @JsonPropertyDescription("承载 API Key 的请求头、查询参数或请求体字段名称")
            String name,
            @JsonPropertyDescription("组合 API Key 最终值的模板，可用于添加厂商要求的前后缀")
            String valueTemplate,
            @JsonPropertyDescription("是否已保存 API Key；响应不返回密钥本身")
            boolean apiKeyConfigured
    ) implements AuthenticationConfiguration {
        @Override
        public String type() {
            return "API_KEY";
        }
    }

    @JsonClassDescription("OAuth2 Client Credentials 取令牌及把令牌注入业务请求的公开配置。")
    public record OAuth2ClientCredentialsAuthentication(
            @JsonPropertyDescription("OAuth2 Token Endpoint 完整地址")
            String tokenUrl,
            @JsonPropertyDescription("OAuth2 Client ID")
            String clientId,
            @JsonPropertyDescription("请求访问令牌时携带的 scope 列表")
            List<String> scopes,
            @JsonPropertyDescription("可选 audience 参数")
            String audience,
            @JsonPropertyDescription("获取到的令牌注入业务请求的位置")
            ValueLocation tokenLocation,
            @JsonPropertyDescription("非默认注入位置使用的请求头、查询参数或请求体字段名称")
            String tokenName,
            @JsonPropertyDescription("令牌最终值模板；可用于添加 Bearer 等前缀")
            String tokenValueTemplate,
            @JsonPropertyDescription("是否已保存 Client Secret；响应不返回密钥本身")
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

    @JsonClassDescription("通过自定义 Token Endpoint 获取短期令牌并注入业务请求的公开配置。")
    public record TokenEndpointAuthentication(
            @JsonPropertyDescription("自定义 Token Endpoint 完整地址")
            String tokenUrl,
            @JsonPropertyDescription("调用 Token Endpoint 使用的 HTTP 方法")
            HttpMethod method,
            @JsonPropertyDescription("Token Endpoint 专用请求头模板")
            List<NamedValue> headers,
            @JsonPropertyDescription("Token Endpoint 请求体模板")
            String bodyTemplate,
            @JsonPropertyDescription("Token Endpoint 用户名")
            String username,
            @JsonPropertyDescription("从 JSON 响应提取访问令牌的 JSON Pointer")
            String tokenPointer,
            @JsonPropertyDescription("从 JSON 响应提取 expires_in 秒数的 JSON Pointer")
            String expiresInPointer,
            @JsonPropertyDescription("无法提取有效期时采用的固定令牌 TTL，单位秒")
            Integer fixedTtlSeconds,
            @JsonPropertyDescription("获取到的令牌注入业务请求的位置")
            ValueLocation tokenLocation,
            @JsonPropertyDescription("非默认注入位置使用的字段名称")
            String tokenName,
            @JsonPropertyDescription("令牌最终值模板")
            String tokenValueTemplate,
            @JsonPropertyDescription("是否已保存 Token Endpoint 密码；响应不返回密码本身")
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
    @JsonClassDescription("HTTP API 执行侧使用的敏感凭据集合；可进入内部 Manifest，但绝不能返回 Web 客户端。")
    public record CredentialBundle(
            @JsonPropertyDescription("Basic 认证密码，仅用于执行侧，不得出现在 Web 响应")
            String password,
            @JsonPropertyDescription("固定 Bearer Token，仅用于执行侧，不得出现在 Web 响应")
            String bearerToken,
            @JsonPropertyDescription("API Key，仅用于执行侧，不得出现在 Web 响应")
            String apiKey,
            @JsonPropertyDescription("OAuth2 Client Secret，仅用于执行侧，不得出现在 Web 响应")
            String clientSecret,
            @JsonPropertyDescription("自定义 Token Endpoint 密码，仅用于执行侧，不得出现在 Web 响应")
            String tokenEndpointPassword,
            @JsonPropertyDescription("签名共享密钥，仅用于执行侧，不得出现在 Web 响应")
            String signingSecret,
            @JsonPropertyDescription("RSA 签名私钥，仅用于执行侧，不得出现在 Web 响应")
            String signingPrivateKey
    ) {
    }

    @JsonClassDescription("HTTP API 数据源的公开连接、默认请求头、超时、限流、重试和凭据配置状态。")
    public record ConnectionConfiguration(
            @JsonPropertyDescription("远端 API 基地址；资源相对路径基于此地址解析")
            String baseUrl,
            @JsonPropertyDescription("每次业务请求都携带的默认请求头")
            List<NamedValue> defaultHeaders,
            @JsonPropertyDescription("建立连接超时时间，单位毫秒")
            int connectTimeoutMs,
            @JsonPropertyDescription("单次 HTTP 请求超时时间，单位毫秒")
            int requestTimeoutMs,
            @JsonPropertyDescription("同一资源相邻请求的最小间隔，单位毫秒")
            int minimumRequestIntervalMs,
            @JsonPropertyDescription("网络错误最大重试次数；0 表示不重试")
            int maxRetries,
            @JsonPropertyDescription("认证配置；只包含非敏感字段及凭据已配置状态")
            AuthenticationConfiguration authentication,
            @JsonPropertyDescription("是否已保存签名共享密钥")
            boolean signingSecretConfigured,
            @JsonPropertyDescription("是否已保存 RSA 签名私钥")
            boolean signingPrivateKeyConfigured
    ) {
        public ConnectionConfiguration {
            defaultHeaders = immutable(defaultHeaders);
        }
    }

    @JsonClassDescription("执行时使用的 HTTP API 连接快照，由公开连接配置和单独注入的敏感凭据组成。")
    public record RuntimeConnection(
            @JsonPropertyDescription("可公开的连接配置")
            ConnectionConfiguration configuration,
            @JsonPropertyDescription("执行时注入的敏感凭据，不得持久化到任务定义或返回客户端")
            CredentialBundle credentials
    ) {
    }

    @JsonClassDescription("请求签名使用的时间戳名称、注入位置和秒或毫秒单位。")
    public record TimestampConfiguration(
            @JsonPropertyDescription("签名时间戳参数名称")
            String name,
            @JsonPropertyDescription("时间戳写入请求头、查询参数或请求体的位置")
            ValueLocation location,
            @JsonPropertyDescription("时间戳单位：秒或毫秒")
            TimestampUnit unit
    ) {
    }

    @JsonClassDescription("请求签名使用的随机数名称和注入位置。")
    public record NonceConfiguration(
            @JsonPropertyDescription("签名随机数参数名称")
            String name,
            @JsonPropertyDescription("随机数写入请求头、查询参数或请求体的位置")
            ValueLocation location
    ) {
    }

    @JsonClassDescription("计算后的签名值如何命名、编码并写入远端请求。")
    public record SignatureOutput(
            @JsonPropertyDescription("签名值参数名称")
            String name,
            @JsonPropertyDescription("签名值写入请求头、查询参数或请求体的位置")
            ValueLocation location,
            @JsonPropertyDescription("签名二进制结果的文本编码方式")
            SignatureEncoding encoding,
            @JsonPropertyDescription("签名最终值模板，可用于添加算法名或厂商前缀")
            String valueTemplate
    ) {
    }

    @JsonClassDescription("远端厂商请求签名协议；定义算法、规范串以及时间戳、随机数和输出位置。")
    public record SigningConfiguration(
            @JsonPropertyDescription("签名算法；NONE 表示不签名")
            SignatureType type,
            @JsonPropertyDescription("待签名规范串模板；字段顺序和分隔符必须符合远端协议")
            String canonicalTemplate,
            @JsonPropertyDescription("可选时间戳参数配置")
            TimestampConfiguration timestamp,
            @JsonPropertyDescription("可选随机数参数配置")
            NonceConfiguration nonce,
            @JsonPropertyDescription("签名结果的注入位置、编码和模板")
            SignatureOutput output
    ) {
        public static SigningConfiguration none() {
            return new SigningConfiguration(SignatureType.NONE, null, null, null, null);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
    @JsonClassDescription("远端 HTTP API 的分页策略；type 决定不分页、页码、偏移量、游标或下一页 URL 结构。")
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

    @JsonClassDescription("单次请求即返回全部允许结果，不执行后续分页请求。")
    public record NoPagination() implements PaginationConfiguration {
        @Override
        public PaginationType type() {
            return PaginationType.NONE;
        }
    }

    @JsonClassDescription("通过页码与每页条数参数遍历远端结果，并用响应指针判断是否结束。")
    public record PageNumberPagination(
            @JsonPropertyDescription("分页参数写入位置")
            ValueLocation location,
            @JsonPropertyDescription("页码参数名称")
            String pageParameter,
            @JsonPropertyDescription("每页条数参数名称")
            String pageSizeParameter,
            @JsonPropertyDescription("首个请求使用的页码；按远端 API 的 0 或 1 起始规则填写")
            int initialPage,
            @JsonPropertyDescription("每页请求条数")
            int pageSize,
            @JsonPropertyDescription("从响应读取是否还有下一页的 JSON Pointer；与 totalPagesPointer 可择一")
            String hasMorePointer,
            @JsonPropertyDescription("从响应读取总页数的 JSON Pointer；与 hasMorePointer 可择一")
            String totalPagesPointer
    ) implements PaginationConfiguration {
        @Override
        public PaginationType type() {
            return PaginationType.PAGE_NUMBER;
        }
    }

    @JsonClassDescription("通过偏移量与批大小参数遍历远端结果，并用响应指针判断是否结束。")
    public record OffsetLimitPagination(
            @JsonPropertyDescription("分页参数写入位置")
            ValueLocation location,
            @JsonPropertyDescription("偏移量参数名称")
            String offsetParameter,
            @JsonPropertyDescription("每批条数参数名称")
            String limitParameter,
            @JsonPropertyDescription("首个请求使用的偏移量，通常为 0")
            int initialOffset,
            @JsonPropertyDescription("每批请求条数")
            int limit,
            @JsonPropertyDescription("从响应读取是否还有下一批的 JSON Pointer；与 totalPointer 可择一")
            String hasMorePointer,
            @JsonPropertyDescription("从响应读取总记录数的 JSON Pointer；与 hasMorePointer 可择一")
            String totalPointer
    ) implements PaginationConfiguration {
        @Override
        public PaginationType type() {
            return PaginationType.OFFSET_LIMIT;
        }
    }

    @JsonClassDescription("把前一响应提取的游标传给下一请求，直到没有下一游标或 hasMore 为 false。")
    public record CursorPagination(
            @JsonPropertyDescription("游标参数写入位置")
            ValueLocation location,
            @JsonPropertyDescription("游标参数名称")
            String cursorParameter,
            @JsonPropertyDescription("首个请求的初始游标；远端无需初始游标时可空")
            String initialCursor,
            @JsonPropertyDescription("从响应读取下一页游标的 JSON Pointer")
            String nextCursorPointer,
            @JsonPropertyDescription("从响应读取是否还有下一页的 JSON Pointer；省略时以下一游标是否存在判断")
            String hasMorePointer
    ) implements PaginationConfiguration {
        @Override
        public PaginationType type() {
            return PaginationType.CURSOR;
        }
    }

    @JsonClassDescription("从响应提取下一页 URL 并继续请求；可强制所有下一页地址与连接基地址同源。")
    public record NextUrlPagination(
            @JsonPropertyDescription("从响应读取下一页 URL 的 JSON Pointer")
            String nextUrlPointer,
            @JsonPropertyDescription("是否强制下一页 URL 与连接 baseUrl 同源；应保持 true 以阻止跳转到其他主机")
            boolean sameOriginOnly
    ) implements PaginationConfiguration {
        @Override
        public PaginationType type() {
            return PaginationType.NEXT_URL;
        }
    }

    @JsonClassDescription("远端异步查询任务的任务 ID 提取、状态轮询、终止状态和结果请求规则。")
    public record AsyncJobConfiguration(
            @JsonPropertyDescription("提交异步任务后用于查询状态的请求模板")
            RequestTemplate statusRequest,
            @JsonPropertyDescription("从提交响应提取任务 ID 的 JSON Pointer")
            String jobIdPointer,
            @JsonPropertyDescription("从状态响应提取任务状态的 JSON Pointer")
            String statusPointer,
            @JsonPropertyDescription("表示任务仍在运行、需要继续轮询的状态值")
            Set<String> runningStatuses,
            @JsonPropertyDescription("表示任务成功、可以读取结果的状态值")
            Set<String> successStatuses,
            @JsonPropertyDescription("表示任务失败、应停止轮询的状态值")
            Set<String> failureStatuses,
            @JsonPropertyDescription("状态轮询间隔，单位毫秒")
            int pollingIntervalMs,
            @JsonPropertyDescription("状态轮询总超时时间，单位毫秒")
            int pollingTimeoutMs,
            @JsonPropertyDescription("远端任务成功后读取结果的请求模板；当前实现必填，不能直接把状态响应作为最终结果")
            RequestTemplate resultRequest
    ) {
        public AsyncJobConfiguration {
            runningStatuses = immutable(runningStatuses);
            successStatuses = immutable(successStatuses);
            failureStatuses = immutable(failureStatuses);
        }
    }

    @JsonClassDescription("一次 HTTP API 拉取的页数、记录数、累计响应字节和总耗时硬上限。")
    public record ExecutionLimits(
            @JsonPropertyDescription("一次拉取允许请求的最大页数")
            int maxPages,
            @JsonPropertyDescription("一次拉取允许返回的最大记录数")
            long maxRows,
            @JsonPropertyDescription("一次拉取允许读取的累计响应字节数")
            long maxResponseBytes,
            @JsonPropertyDescription("一次拉取允许执行的最长时间，单位秒")
            int maxDurationSeconds
    ) {
    }

    @JsonClassDescription("从远端单条 JSON 记录提取并转换出的一个平台类型输出字段。")
    public record OutputField(
            @JsonPropertyDescription("输出列名；在一个资源内唯一")
            String name,
            @JsonPropertyDescription("从单条 JSON 记录提取字段值的 JSON Pointer")
            String jsonPointer,
            @JsonPropertyDescription("字段的平台类型及长度、精度等参数")
            PlatformTypeDefinition type,
            @JsonPropertyDescription("源值是否允许为 null 或缺失")
            boolean nullable,
            @JsonPropertyDescription("字段业务含义、单位或口径说明")
            String comment
    ) {
    }

    @JsonClassDescription("一个已发布 HTTP API 资源的完整只读执行定义，包括请求、签名、分页、异步轮询、输出字段和安全上限。")
    public record ResourceDefinition(
            @JsonPropertyDescription("资源 UUID")
            UUID id,
            @JsonPropertyDescription("所属 HTTP API 数据源 UUID")
            UUID dataSourceId,
            @JsonPropertyDescription("数据源内唯一资源编码")
            String code,
            @JsonPropertyDescription("资源显示名称")
            String name,
            @JsonPropertyDescription("连接器类型；通用资源通常为 GENERIC_HTTP")
            String connectorType,
            @JsonPropertyDescription("是否允许任务执行此资源")
            boolean enabled,
            @JsonPropertyDescription("业务请求模板")
            RequestTemplate request,
            @JsonPropertyDescription("请求签名配置")
            SigningConfiguration signing,
            @JsonPropertyDescription("单次、分页或异步任务调用模式")
            InvocationType invocationType,
            @JsonPropertyDescription("分页配置；非分页资源为空")
            PaginationConfiguration pagination,
            @JsonPropertyDescription("异步任务轮询配置；非异步资源为空")
            AsyncJobConfiguration asyncJob,
            @JsonPropertyDescription("定位最终响应记录数组的 JSON Pointer")
            String recordsPointer,
            @JsonPropertyDescription("输出字段定义")
            List<OutputField> outputFields,
            @JsonPropertyDescription("执行安全上限")
            ExecutionLimits limits
    ) {
        public ResourceDefinition {
            outputFields = immutable(outputFields);
        }
    }

    @JsonClassDescription("Task Engine 发起一次 HTTP API 拉取所需的运行时连接、发布资源快照和临时模板参数。")
    public record PullRequest(
            @JsonPropertyDescription("包含执行凭据的运行时连接快照")
            RuntimeConnection connection,
            @JsonPropertyDescription("已发布的资源定义快照")
            ResourceDefinition resource,
            @JsonPropertyDescription("本次拉取替换模板变量的运行时参数")
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

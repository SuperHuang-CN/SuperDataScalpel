package cn.superhuang.datascalpel.taskengine.httpapi;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.datascalpel.taskengine.http.JsonSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Built-in, dependency-free HTTP/JSON connector used by standalone runners. */
public final class GenericHttpApiPullConnector implements HttpApiPullConnector {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)}");
    private static final Pattern SENSITIVE_RUNTIME_PARAMETER = Pattern.compile(
            "(?i).*(password|passwd|secret|token|credential|api[-_.]?key|access[-_.]?key|signature).*"
    );
    private static final int TOKEN_RESPONSE_LIMIT = 1024 * 1024;
    static final int MAX_BATCH_ROWS = 10_000;

    private final ObjectMapper objectMapper = JsonSupport.strictObjectMapper();
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();
    private final Object tokenRefreshMonitor = new Object();

    @Override
    public String type() {
        return HttpApiContracts.GENERIC_CONNECTOR;
    }

    @Override
    public HttpApiPullResult pull(HttpApiContracts.PullRequest request) {
        List<List<Object>> rows = new ArrayList<>();
        HttpApiPullSummary summary = pullBatches(request, batch -> rows.addAll(batch.rows()));
        return new HttpApiPullResult(rows, summary.pages(), summary.responseBytes());
    }

    @Override
    public HttpApiPullSummary pullBatches(
            HttpApiContracts.PullRequest request,
            HttpApiPullBatchConsumer consumer
    ) {
        validateRequest(request);
        Objects.requireNonNull(consumer, "consumer");
        PullContext context = new PullContext(request.connection(), request.resource().limits());
        Map<String, String> variables = variables(request.connection(), request.runtimeParameters());
        HttpApiContracts.RequestTemplate resultRequest = request.resource().request();
        if (request.resource().invocationType() == HttpApiContracts.InvocationType.ASYNC_JOB) {
            Response submission = execute(
                    context, request.connection(), request.resource(), resultRequest, variables,
                    new PageState(), null);
            String jobId = requiredText(
                    submission.json.at(request.resource().asyncJob().jobIdPointer()),
                    "API_ASYNC_JOB_ID_MISSING", "异步接口未返回 Job ID");
            variables.put("jobId", jobId);
            waitForJob(context, request, variables);
            resultRequest = request.resource().asyncJob().resultRequest();
        }

        PageState page = new PageState();
        HttpApiContracts.PaginationConfiguration pagination = request.resource().pagination();
        page.initialize(pagination);
        boolean paginated = pagination != null && pagination.type() != HttpApiContracts.PaginationType.NONE;
        int pages = 0;
        int batches = 0;
        long totalRows = 0;
        while (true) {
            context.checkActive();
            if (++pages > context.limits.maxPages()) {
                throw failure("API_MAX_PAGES_EXCEEDED", "API 返回页数超过限制");
            }
            Response response = execute(
                    context, request.connection(), request.resource(), resultRequest, variables, page, pagination);
            List<JsonNode> records = records(response.json, request.resource().recordsPointer());
            List<List<Object>> batchRows = new ArrayList<>(Math.min(MAX_BATCH_ROWS, records.size()));
            for (JsonNode record : records) {
                if (totalRows >= context.limits.maxRows()) {
                    throw failure("API_MAX_ROWS_EXCEEDED", "API 返回记录数超过限制");
                }
                List<Object> row = request.resource().outputFields().stream()
                        .map(field -> outputValue(record, field)).toList();
                totalRows++;
                batchRows.add(row);
                if (batchRows.size() == MAX_BATCH_ROWS) {
                    context.checkActive();
                    consumer.accept(new HttpApiPullBatch(++batches, pages, batchRows));
                    batchRows = new ArrayList<>(Math.min(MAX_BATCH_ROWS, records.size()));
                }
            }
            if (!batchRows.isEmpty()) {
                context.checkActive();
                consumer.accept(new HttpApiPullBatch(++batches, pages, batchRows));
            }
            if (!paginated || !advance(
                    pagination, page, response.json, records.size(), request.connection().configuration().baseUrl())) {
                break;
            }
        }
        return new HttpApiPullSummary(pages, batches, totalRows, context.responseBytes);
    }

    private void waitForJob(
            PullContext context,
            HttpApiContracts.PullRequest request,
            Map<String, String> variables
    ) {
        HttpApiContracts.AsyncJobConfiguration async = request.resource().asyncJob();
        Instant deadline = Instant.now().plusMillis(async.pollingTimeoutMs());
        while (true) {
            Response response = execute(
                    context, request.connection(), request.resource(), async.statusRequest(),
                    variables, new PageState(), null);
            String status = requiredText(response.json.at(async.statusPointer()),
                    "API_ASYNC_STATUS_MISSING", "异步接口未返回任务状态");
            if (async.successStatuses().contains(status)) return;
            if (async.failureStatuses().contains(status)) {
                throw failure("API_ASYNC_JOB_FAILED", "远程异步任务执行失败，状态：" + status);
            }
            if (!async.runningStatuses().isEmpty() && !async.runningStatuses().contains(status)) {
                throw failure("API_ASYNC_STATUS_UNKNOWN", "远程异步任务返回未知状态：" + status);
            }
            if (Instant.now().plusMillis(async.pollingIntervalMs()).isAfter(deadline)) {
                throw failure("API_ASYNC_TIMEOUT", "等待远程异步任务超时");
            }
            sleep(async.pollingIntervalMs());
        }
    }

    private Response execute(
            PullContext context,
            HttpApiContracts.RuntimeConnection connection,
            HttpApiContracts.ResourceDefinition resource,
            HttpApiContracts.RequestTemplate template,
            Map<String, String> baseVariables,
            PageState page,
            HttpApiContracts.PaginationConfiguration pagination
    ) {
        page.initialize(pagination);
        boolean refreshed = false;
        boolean forceTokenRefresh = false;
        int retry = 0;
        while (true) {
            context.checkActive();
            Map<String, String> currentVariables = new LinkedHashMap<>(baseVariables);
            page.addVariables(currentVariables, pagination);
            MutableRequest request = prepare(
                    connection, resource, template, currentVariables, page, pagination, forceTokenRefresh);
            forceTokenRefresh = false;
            try {
                context.rateLimit();
                HttpResponse<InputStream> response = send(connection, toRequest(connection, request));
                long remaining = context.limits.maxResponseBytes() - context.responseBytes;
                byte[] body = readLimited(response.body(), remaining);
                context.responseBytes += body.length;
                int status = response.statusCode();
                if ((status == 401 || status == 403) && dynamicAuthentication(connection) && !refreshed) {
                    refreshed = true;
                    forceTokenRefresh = true;
                    continue;
                }
                if (retryable(status) && retry < connection.configuration().maxRetries()) {
                    sleep(retryDelay(response, retry++));
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new HttpApiPullException(
                            status == 401 || status == 403 ? "API_AUTHENTICATION_FAILED" : "API_HTTP_ERROR",
                            "API 请求失败，HTTP " + status, status, null);
                }
                try {
                    JsonNode json = body.length == 0 ? objectMapper.createObjectNode() : objectMapper.readTree(body);
                    return new Response(status, json);
                } catch (IOException exception) {
                    throw new HttpApiPullException(
                            "API_RESPONSE_INVALID_JSON", "API 响应不是有效的 JSON", status, exception);
                }
            } catch (HttpApiPullException exception) {
                if (isNetworkFailure(exception) && retry++ < connection.configuration().maxRetries()) {
                    sleep(retryDelay(null, retry));
                    continue;
                }
                throw exception;
            }
        }
    }

    private MutableRequest prepare(
            HttpApiContracts.RuntimeConnection connection,
            HttpApiContracts.ResourceDefinition resource,
            HttpApiContracts.RequestTemplate template,
            Map<String, String> variables,
            PageState page,
            HttpApiContracts.PaginationConfiguration pagination,
            boolean forceTokenRefresh
    ) {
        URI uri = page.nextUrl == null
                ? combine(connection.configuration().baseUrl(), render(template.path(), variables))
                : page.nextUrl;
        List<HttpApiContracts.NamedValue> query = new ArrayList<>();
        if (page.nextUrl == null) {
            template.queryParameters().forEach(value -> query.add(new HttpApiContracts.NamedValue(
                    value.name(), render(value.value(), variables))));
        }
        Map<String, String> headers = new LinkedHashMap<>();
        MutableRequest request = new MutableRequest(
                template.method(), uri, query, headers, render(template.bodyTemplate(), variables));
        connection.configuration().defaultHeaders().forEach(value ->
                headers.put(value.name(), render(value.value(), variables)));
        template.headers().forEach(value -> headers.put(value.name(), render(value.value(), variables)));
        applyPagination(pagination, request, page);
        applyAuthentication(connection, request, variables, forceTokenRefresh);
        applySigning(connection, resource.signing(), request, variables);
        return request;
    }

    private void applyAuthentication(
            HttpApiContracts.RuntimeConnection connection,
            MutableRequest request,
            Map<String, String> variables,
            boolean forceRefresh
    ) {
        HttpApiContracts.CredentialBundle credentials = connection.credentials();
        switch (connection.configuration().authentication()) {
            case HttpApiContracts.NoneAuthentication ignored -> {
            }
            case HttpApiContracts.BasicAuthentication basic -> request.headers.put(
                    "Authorization", "Basic " + Base64.getEncoder().encodeToString(
                            (basic.username() + ":" + credentials.password()).getBytes(StandardCharsets.UTF_8)));
            case HttpApiContracts.BearerTokenAuthentication ignored ->
                    request.headers.put("Authorization", "Bearer " + credentials.bearerToken());
            case HttpApiContracts.ApiKeyAuthentication apiKey ->
                    add(request, apiKey.location(), apiKey.name(), render(apiKey.valueTemplate(), variables));
            case HttpApiContracts.OAuth2ClientCredentialsAuthentication oauth -> {
                String token = token(connection, variables, forceRefresh);
                variables.put("token", token);
                add(request, oauth.tokenLocation(), oauth.tokenName(), render(oauth.tokenValueTemplate(), variables));
            }
            case HttpApiContracts.TokenEndpointAuthentication endpoint -> {
                String token = token(connection, variables, forceRefresh);
                variables.put("token", token);
                add(request, endpoint.tokenLocation(), endpoint.tokenName(),
                        render(endpoint.tokenValueTemplate(), variables));
            }
        }
    }

    private String token(
            HttpApiContracts.RuntimeConnection connection,
            Map<String, String> variables,
            boolean forceRefresh
    ) {
        String key = tokenKey(connection, variables);
        if (forceRefresh) tokenCache.remove(key);
        CachedToken cached = tokenCache.get(key);
        if (cached != null && cached.valid()) return cached.value;
        synchronized (tokenRefreshMonitor) {
            cached = tokenCache.get(key);
            if (cached != null && cached.valid()) return cached.value;
            CachedToken acquired = acquireToken(connection, variables);
            tokenCache.put(key, acquired);
            return acquired.value;
        }
    }

    private CachedToken acquireToken(
            HttpApiContracts.RuntimeConnection connection,
            Map<String, String> inherited
    ) {
        return switch (connection.configuration().authentication()) {
            case HttpApiContracts.OAuth2ClientCredentialsAuthentication oauth -> {
                Map<String, String> form = new LinkedHashMap<>();
                form.put("grant_type", "client_credentials");
                form.put("client_id", oauth.clientId());
                form.put("client_secret", connection.credentials().clientSecret());
                if (!oauth.scopes().isEmpty()) form.put("scope", String.join(" ", oauth.scopes()));
                if (text(oauth.audience())) form.put("audience", oauth.audience());
                HttpRequest tokenRequest = HttpRequest.newBuilder(URI.create(oauth.tokenUrl()))
                        .timeout(Duration.ofMillis(connection.configuration().requestTimeoutMs()))
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(form(form)))
                        .build();
                JsonNode json = tokenResponse(connection, tokenRequest);
                yield cached(requiredText(json.at("/access_token"), "API_TOKEN_MISSING",
                        "OAuth2 响应未返回 access_token"), number(json.at("/expires_in"), 300));
            }
            case HttpApiContracts.TokenEndpointAuthentication endpoint -> {
                Map<String, String> variables = new LinkedHashMap<>(inherited);
                variables.put("credential.username", empty(endpoint.username()));
                variables.put("credential.password", empty(connection.credentials().tokenEndpointPassword()));
                variables.put("credential.tokenEndpointPassword",
                        empty(connection.credentials().tokenEndpointPassword()));
                HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint.tokenUrl()))
                        .timeout(Duration.ofMillis(connection.configuration().requestTimeoutMs()))
                        .header("Accept", "application/json");
                endpoint.headers().forEach(value -> builder.header(value.name(), render(value.value(), variables)));
                if (endpoint.method() == HttpApiContracts.HttpMethod.POST) {
                    String body = render(endpoint.bodyTemplate(), variables);
                    if (endpoint.headers().stream().noneMatch(value ->
                            "content-type".equalsIgnoreCase(value.name()))) {
                        builder.header("Content-Type", body != null && body.stripLeading().startsWith("{")
                                ? "application/json" : "application/x-www-form-urlencoded");
                    }
                    builder.POST(HttpRequest.BodyPublishers.ofString(empty(body)));
                } else {
                    builder.GET();
                }
                JsonNode json = tokenResponse(connection, builder.build());
                long ttl = endpoint.expiresInPointer() == null
                        ? endpoint.fixedTtlSeconds()
                        : number(json.at(endpoint.expiresInPointer()), endpoint.fixedTtlSeconds() == null
                        ? 300 : endpoint.fixedTtlSeconds());
                yield cached(requiredText(json.at(endpoint.tokenPointer()), "API_TOKEN_MISSING",
                        "Token Endpoint 响应未返回 Token"), ttl);
            }
            default -> throw failure("API_AUTHENTICATION_CONFIGURATION_INVALID", "当前鉴权方式不生成 Token");
        };
    }

    private JsonNode tokenResponse(HttpApiContracts.RuntimeConnection connection, HttpRequest request) {
        HttpResponse<InputStream> response = send(connection, request);
        byte[] body = readLimited(response.body(), TOKEN_RESPONSE_LIMIT);
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new HttpApiPullException(
                    "API_TOKEN_REQUEST_FAILED", "运行时 Token 获取失败，HTTP " + response.statusCode(),
                    response.statusCode(), null);
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException exception) {
            throw new HttpApiPullException(
                    "API_TOKEN_RESPONSE_INVALID", "Token 接口响应不是有效的 JSON",
                    response.statusCode(), exception);
        }
    }

    private void applyPagination(
            HttpApiContracts.PaginationConfiguration pagination,
            MutableRequest request,
            PageState state
    ) {
        if (pagination == null) return;
        switch (pagination) {
            case HttpApiContracts.NoPagination ignored -> {
            }
            case HttpApiContracts.PageNumberPagination page -> {
                add(request, page.location(), page.pageParameter(), state.page.toString());
                add(request, page.location(), page.pageSizeParameter(), Integer.toString(page.pageSize()));
            }
            case HttpApiContracts.OffsetLimitPagination offset -> {
                add(request, offset.location(), offset.offsetParameter(), state.offset.toString());
                add(request, offset.location(), offset.limitParameter(), Integer.toString(offset.limit()));
            }
            case HttpApiContracts.CursorPagination cursor -> {
                if (state.cursor != null) add(request, cursor.location(), cursor.cursorParameter(), state.cursor);
            }
            case HttpApiContracts.NextUrlPagination ignored -> {
            }
        }
    }

    private void applySigning(
            HttpApiContracts.RuntimeConnection connection,
            HttpApiContracts.SigningConfiguration signing,
            MutableRequest request,
            Map<String, String> variables
    ) {
        if (signing == null || signing.type() == HttpApiContracts.SignatureType.NONE) return;
        if (signing.timestamp() != null) {
            long value = signing.timestamp().unit() == HttpApiContracts.TimestampUnit.SECONDS
                    ? Instant.now().getEpochSecond() : System.currentTimeMillis();
            variables.put("timestamp", Long.toString(value));
            add(request, signing.timestamp().location(), signing.timestamp().name(), Long.toString(value));
        }
        if (signing.nonce() != null) {
            String value = UUID.randomUUID().toString().replace("-", "");
            variables.put("nonce", value);
            add(request, signing.nonce().location(), signing.nonce().name(), value);
        }
        URI unsigned = withQuery(request.uri, request.query);
        variables.put("request.method", request.method.name());
        variables.put("request.path", unsigned.getRawPath());
        variables.put("request.query", empty(unsigned.getRawQuery()));
        variables.put("request.body", empty(request.body));
        variables.put("request.bodyHash", hex(sha256(empty(request.body).getBytes(StandardCharsets.UTF_8)), false));
        variables.put("credential.signingSecret", empty(connection.credentials().signingSecret()));
        byte[] signature = sign(signing.type(), render(signing.canonicalTemplate(), variables), connection.credentials());
        String encoded = switch (signing.output().encoding()) {
            case HEX_LOWERCASE -> hex(signature, false);
            case HEX_UPPERCASE -> hex(signature, true);
            case BASE64 -> Base64.getEncoder().encodeToString(signature);
            case BASE64_URL -> Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        };
        variables.put("signature", encoded);
        String value = text(signing.output().valueTemplate())
                ? render(signing.output().valueTemplate(), variables) : encoded;
        add(request, signing.output().location(), signing.output().name(), value);
    }

    private static byte[] sign(
            HttpApiContracts.SignatureType type,
            String canonical,
            HttpApiContracts.CredentialBundle credentials
    ) {
        byte[] bytes = canonical.getBytes(StandardCharsets.UTF_8);
        try {
            return switch (type) {
                case MD5 -> MessageDigest.getInstance("MD5").digest(bytes);
                case HMAC_SHA256 -> hmac("HmacSHA256", bytes, credentials.signingSecret());
                case HMAC_SHA512 -> hmac("HmacSHA512", bytes, credentials.signingSecret());
                case RSA_SHA256 -> {
                    Signature signer = Signature.getInstance("SHA256withRSA");
                    signer.initSign(privateKey(credentials.signingPrivateKey()));
                    signer.update(bytes);
                    yield signer.sign();
                }
                case NONE -> throw new IllegalArgumentException("NONE does not sign");
            };
        } catch (Exception exception) {
            throw new HttpApiPullException("API_SIGNING_FAILED", "无法生成 API 请求签名", null, exception);
        }
    }

    private static boolean advance(
            HttpApiContracts.PaginationConfiguration pagination,
            PageState state,
            JsonNode response,
            int count,
            String baseUrl
    ) {
        return switch (pagination) {
            case HttpApiContracts.NoPagination ignored -> false;
            case HttpApiContracts.PageNumberPagination page -> {
                if (count == 0 || falseAt(response, page.hasMorePointer())) yield false;
                long total = longAt(response, page.totalPagesPointer(), -1);
                long consumedPages = (long) state.page - page.initialPage() + 1;
                if (total >= 0 && consumedPages >= total) yield false;
                if (total < 0 && !text(page.hasMorePointer()) && count < page.pageSize()) yield false;
                state.page++;
                yield true;
            }
            case HttpApiContracts.OffsetLimitPagination offset -> {
                if (count == 0 || falseAt(response, offset.hasMorePointer())) yield false;
                long total = longAt(response, offset.totalPointer(), -1);
                if (total >= 0 && (long) state.offset + count >= total) yield false;
                if (total < 0 && !text(offset.hasMorePointer()) && count < offset.limit()) yield false;
                state.offset += offset.limit();
                yield true;
            }
            case HttpApiContracts.CursorPagination cursor -> {
                if (count == 0 || falseAt(response, cursor.hasMorePointer())) yield false;
                String next = textAt(response, cursor.nextCursorPointer());
                if (!text(next)) yield false;
                if (!state.seen.add("cursor:" + next)) {
                    throw failure("API_PAGINATION_LOOP", "API 返回了重复 Cursor");
                }
                state.cursor = next;
                yield true;
            }
            case HttpApiContracts.NextUrlPagination next -> {
                if (count == 0) yield false;
                String value = textAt(response, next.nextUrlPointer());
                if (!text(value)) yield false;
                URI resolved = URI.create(baseUrl).resolve(value);
                if (next.sameOriginOnly() && !sameOrigin(URI.create(baseUrl), resolved)) {
                    throw failure("API_NEXT_URL_ORIGIN_REJECTED", "API 返回的下一页地址不属于数据源同源地址");
                }
                if (!state.seen.add("url:" + resolved)) {
                    throw failure("API_PAGINATION_LOOP", "API 返回了重复下一页地址");
                }
                state.nextUrl = resolved;
                yield true;
            }
        };
    }

    private Object outputValue(JsonNode record, HttpApiContracts.OutputField field) {
        JsonNode value = field.jsonPointer().isEmpty() ? record : record.at(field.jsonPointer());
        if (value.isMissingNode() || value.isNull()) {
            if (!field.nullable()) {
                throw failure("API_REQUIRED_FIELD_MISSING", "API 响应缺少必填字段：" + field.name());
            }
            return null;
        }
        try {
            return switch (field.type().type()) {
                case BOOLEAN -> booleanValue(value);
                case BYTE -> decimalValue(value).byteValueExact();
                case SHORT -> decimalValue(value).shortValueExact();
                case INTEGER -> decimalValue(value).intValueExact();
                case LONG -> decimalValue(value).longValueExact();
                case FLOAT -> Float.parseFloat(numberText(value));
                case DOUBLE -> Double.parseDouble(numberText(value));
                case DECIMAL -> decimal(decimalValue(value), field);
                case STRING -> checkedString(value.isTextual() ? value.textValue() : value.toString(), field);
                case BINARY -> value.isBinary() ? value.binaryValue() : Base64.getDecoder().decode(value.asText());
                case DATE -> Date.valueOf(LocalDate.parse(value.asText()));
                case TIMESTAMP -> timestamp(value.asText());
                case TIMESTAMP_NTZ -> LocalDateTime.parse(value.asText());
                case GEOMETRY -> throw new IllegalArgumentException("HTTP API 字段不支持 Geometry 类型");
            };
        } catch (RuntimeException | IOException exception) {
            throw new HttpApiPullException(
                    "API_FIELD_CONVERSION_FAILED", "API 字段无法转换：" + field.name(), null, exception);
        }
    }

    private static boolean booleanValue(JsonNode value) {
        if (value.isBoolean()) return value.booleanValue();
        String text = value.asText();
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;
        throw new IllegalArgumentException("invalid boolean");
    }

    private static BigDecimal decimalValue(JsonNode value) {
        return new BigDecimal(numberText(value));
    }

    private static String numberText(JsonNode value) {
        if (!value.isNumber() && !value.isTextual()) {
            throw new IllegalArgumentException("not a number");
        }
        return value.asText();
    }

    private static BigDecimal decimal(BigDecimal value, HttpApiContracts.OutputField field) {
        BigDecimal scaled = value.setScale(field.type().scale(), RoundingMode.UNNECESSARY);
        if (scaled.precision() > field.type().precision()) {
            throw new ArithmeticException("decimal precision overflow");
        }
        return scaled;
    }

    private static String checkedString(String value, HttpApiContracts.OutputField field) {
        if (field.type().length() != null && value.codePointCount(0, value.length()) > field.type().length()) {
            throw new IllegalArgumentException("string length overflow");
        }
        return value;
    }

    private static Timestamp timestamp(String value) {
        try {
            return Timestamp.from(Instant.parse(value));
        } catch (RuntimeException ignored) {
            return Timestamp.valueOf(LocalDateTime.parse(value));
        }
    }

    private void add(
            MutableRequest request,
            HttpApiContracts.ValueLocation location,
            String name,
            String value
    ) {
        switch (location) {
            case HEADER -> request.headers.put(name, value);
            case QUERY -> request.query.add(new HttpApiContracts.NamedValue(name, value));
            case BODY -> request.body = bodyValue(request.body, name, value);
        }
    }

    private String bodyValue(String body, String name, String value) {
        try {
            JsonNode parsed = text(body) ? objectMapper.readTree(body) : objectMapper.createObjectNode();
            if (!(parsed instanceof ObjectNode object)) {
                throw new IllegalArgumentException("body is not an object");
            }
            object.put(name, value);
            return objectMapper.writeValueAsString(object);
        } catch (IOException | RuntimeException exception) {
            throw new HttpApiPullException(
                    "API_REQUEST_BODY_INVALID", "分页、时间戳或签名写入 Body 时，Body 必须是 JSON 对象",
                    null, exception);
        }
    }

    private static Map<String, String> variables(
            HttpApiContracts.RuntimeConnection connection,
            List<HttpApiContracts.RuntimeParameter> runtime
    ) {
        Map<String, String> variables = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        for (HttpApiContracts.RuntimeParameter parameter : runtime == null
                ? List.<HttpApiContracts.RuntimeParameter>of() : runtime) {
            if (parameter == null || !text(parameter.name()) || parameter.value() == null
                    || SENSITIVE_RUNTIME_PARAMETER.matcher(parameter.name()).matches()
                    || !names.add(parameter.name())) {
                throw failure("API_RUNTIME_PARAMETER_INVALID", "API 运行时参数无效或名称重复");
            }
            variables.put("runtime." + parameter.name(), parameter.value());
        }
        HttpApiContracts.CredentialBundle c = connection.credentials();
        variables.put("credential.password", empty(c.password()));
        variables.put("credential.bearerToken", empty(c.bearerToken()));
        variables.put("credential.apiKey", empty(c.apiKey()));
        variables.put("credential.clientSecret", empty(c.clientSecret()));
        variables.put("credential.tokenEndpointPassword", empty(c.tokenEndpointPassword()));
        variables.put("credential.signingSecret", empty(c.signingSecret()));
        switch (connection.configuration().authentication()) {
            case HttpApiContracts.BasicAuthentication basic -> variables.put("credential.username", basic.username());
            case HttpApiContracts.OAuth2ClientCredentialsAuthentication oauth ->
                    variables.put("credential.clientId", oauth.clientId());
            case HttpApiContracts.TokenEndpointAuthentication endpoint ->
                    variables.put("credential.username", empty(endpoint.username()));
            default -> {
            }
        }
        return variables;
    }

    private static String render(String template, Map<String, String> variables) {
        if (template == null) return null;
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String replacement = variables.get(matcher.group(1));
            if (replacement == null) {
                throw failure("API_TEMPLATE_VALUE_MISSING", "API 请求缺少模板参数：" + matcher.group(1));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static HttpRequest toRequest(
            HttpApiContracts.RuntimeConnection connection,
            MutableRequest request
    ) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(withQuery(request.uri, request.query))
                .timeout(Duration.ofMillis(connection.configuration().requestTimeoutMs()))
                .header("Accept", "application/json");
        request.headers.forEach(builder::header);
        if (request.method == HttpApiContracts.HttpMethod.POST) {
            if (request.headers.keySet().stream().noneMatch(name -> "content-type".equalsIgnoreCase(name))) {
                builder.header("Content-Type", "application/json");
            }
            builder.POST(HttpRequest.BodyPublishers.ofString(empty(request.body)));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private static HttpResponse<InputStream> send(
            HttpApiContracts.RuntimeConnection connection,
            HttpRequest request
    ) {
        try {
            return HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(connection.configuration().connectTimeoutMs()))
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException exception) {
            throw new HttpApiPullException("API_NETWORK_ERROR", "无法连接 HTTP API", null, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HttpApiPullException("API_REQUEST_INTERRUPTED", "HTTP API 请求已取消", null, exception);
        }
    }

    private static byte[] readLimited(InputStream stream, long maxBytes) {
        if (maxBytes < 0) throw failure("API_MAX_RESPONSE_BYTES_EXCEEDED", "API 响应大小超过限制");
        int limit = (int) Math.min(Integer.MAX_VALUE - 1L, maxBytes);
        try (stream) {
            byte[] body = stream.readNBytes(limit + 1);
            if (body.length > limit) {
                throw failure("API_MAX_RESPONSE_BYTES_EXCEEDED", "API 响应大小超过限制");
            }
            return body;
        } catch (IOException exception) {
            throw new HttpApiPullException("API_RESPONSE_READ_FAILED", "无法读取 API 响应", null, exception);
        }
    }

    private static List<JsonNode> records(JsonNode root, String pointer) {
        JsonNode node = pointer == null || pointer.isEmpty() ? root : root.at(pointer);
        if (node.isMissingNode() || node.isNull()) return List.of();
        if (!node.isArray()) return List.of(node);
        List<JsonNode> result = new ArrayList<>();
        node.forEach(result::add);
        return List.copyOf(result);
    }

    private static URI combine(String base, String path) {
        return URI.create(base.replaceFirst("/+$", "") + "/" + path.replaceFirst("^/+", ""));
    }

    private static URI withQuery(URI uri, List<HttpApiContracts.NamedValue> query) {
        StringBuilder result = new StringBuilder(uri.toString());
        boolean present = uri.getRawQuery() != null;
        for (HttpApiContracts.NamedValue value : query) {
            result.append(present ? '&' : '?');
            present = true;
            result.append(url(value.name())).append('=').append(url(value.value()));
        }
        return URI.create(result.toString());
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> url(entry.getKey()) + "=" + url(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static byte[] hmac(String algorithm, byte[] value, String secret) throws Exception {
        Mac mac = Mac.getInstance(algorithm);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), algorithm));
        return mac.doFinal(value);
    }

    private static PrivateKey privateKey(String pem) throws Exception {
        String encoded = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)));
    }

    private static String tokenKey(
            HttpApiContracts.RuntimeConnection connection,
            Map<String, String> variables
    ) {
        String material = connection.configuration().baseUrl()
                + "|" + connection.configuration().authentication()
                + "|" + connection.credentials().clientSecret()
                + "|" + connection.credentials().tokenEndpointPassword()
                + "|" + variables.entrySet().stream().filter(entry -> entry.getKey().startsWith("runtime."))
                .sorted(Map.Entry.comparingByKey()).toList();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(
                sha256(material.getBytes(StandardCharsets.UTF_8)));
    }

    private void invalidateToken(
            HttpApiContracts.RuntimeConnection connection,
            Map<String, String> variables
    ) {
        tokenCache.remove(tokenKey(connection, variables));
    }

    private static CachedToken cached(String value, long ttlSeconds) {
        long ttl = Math.max(5, ttlSeconds - Math.min(60, Math.max(1, ttlSeconds / 10)));
        return new CachedToken(value, Instant.now().plusSeconds(ttl));
    }

    private static boolean dynamicAuthentication(HttpApiContracts.RuntimeConnection connection) {
        return connection.configuration().authentication() instanceof HttpApiContracts.OAuth2ClientCredentialsAuthentication
                || connection.configuration().authentication() instanceof HttpApiContracts.TokenEndpointAuthentication;
    }

    private static boolean retryable(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    private static boolean isNetworkFailure(HttpApiPullException exception) {
        return "API_NETWORK_ERROR".equals(exception.code());
    }

    private static long retryDelay(HttpResponse<?> response, int retry) {
        if (response != null) {
            String value = response.headers().firstValue("Retry-After").orElse(null);
            if (value != null) {
                try {
                    return Math.min(30_000, Math.max(0, Long.parseLong(value) * 1000));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Math.min(2_000, 100L << Math.min(5, Math.max(0, retry)));
    }

    private static String requiredText(JsonNode value, String code, String message) {
        String result = value == null || value.isMissingNode() || value.isNull() ? null : value.asText();
        if (!text(result)) throw failure(code, message);
        return result;
    }

    private static String textAt(JsonNode root, String pointer) {
        if (!text(pointer)) return null;
        JsonNode value = root.at(pointer);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static long longAt(JsonNode root, String pointer, long fallback) {
        return text(pointer) ? number(root.at(pointer), fallback) : fallback;
    }

    private static long number(JsonNode value, long fallback) {
        if (value == null || value.isMissingNode() || value.isNull()) return fallback;
        try {
            return value.isNumber() ? value.longValue() : Long.parseLong(value.asText());
        } catch (RuntimeException exception) {
            return fallback;
        }
    }

    private static boolean falseAt(JsonNode root, String pointer) {
        if (!text(pointer)) return false;
        JsonNode value = root.at(pointer);
        return !value.isMissingNode() && !value.isNull()
                && (value.isBoolean() ? !value.booleanValue() : "false".equalsIgnoreCase(value.asText()));
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && port(left) == port(right);
    }

    private static int port(URI value) {
        return value.getPort() >= 0 ? value.getPort() : "https".equalsIgnoreCase(value.getScheme()) ? 443 : 80;
    }

    private static byte[] sha256(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String hex(byte[] value, boolean uppercase) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) result.append(String.format(uppercase ? "%02X" : "%02x", item));
        return result.toString();
    }

    private static void validateRequest(HttpApiContracts.PullRequest request) {
        if (request == null || request.connection() == null || request.connection().configuration() == null
                || request.connection().credentials() == null || request.resource() == null
                || request.resource().request() == null || request.resource().limits() == null) {
            throw failure("API_INVALID_CONFIGURATION", "HTTP API 拉取配置不完整");
        }
    }

    private static HttpApiPullException failure(String code, String message) {
        return new HttpApiPullException(code, message);
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }

    private static boolean text(String value) {
        return value != null && !value.isBlank();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HttpApiPullException("API_REQUEST_INTERRUPTED", "HTTP API 请求已取消", null, exception);
        }
    }

    private record Response(int status, JsonNode json) {
    }

    private record CachedToken(String value, Instant expiresAt) {
        private boolean valid() {
            return Instant.now().isBefore(expiresAt);
        }
    }

    private static final class MutableRequest {
        private final HttpApiContracts.HttpMethod method;
        private final URI uri;
        private final List<HttpApiContracts.NamedValue> query;
        private final Map<String, String> headers;
        private String body;

        private MutableRequest(
                HttpApiContracts.HttpMethod method,
                URI uri,
                List<HttpApiContracts.NamedValue> query,
                Map<String, String> headers,
                String body
        ) {
            this.method = method;
            this.uri = uri;
            this.query = query;
            this.headers = headers;
            this.body = body;
        }
    }

    private static final class PageState {
        private Integer page;
        private Integer offset;
        private String cursor;
        private URI nextUrl;
        private final Set<String> seen = new HashSet<>();

        private void initialize(HttpApiContracts.PaginationConfiguration pagination) {
            if (pagination instanceof HttpApiContracts.PageNumberPagination value && page == null) {
                page = value.initialPage();
            } else if (pagination instanceof HttpApiContracts.OffsetLimitPagination value && offset == null) {
                offset = value.initialOffset();
            } else if (pagination instanceof HttpApiContracts.CursorPagination value && cursor == null) {
                cursor = value.initialCursor();
            }
        }

        private void addVariables(
                Map<String, String> variables,
                HttpApiContracts.PaginationConfiguration pagination
        ) {
            if (page != null) variables.put("page", page.toString());
            if (offset != null) variables.put("offset", offset.toString());
            if (cursor != null) variables.put("cursor", cursor);
            if (pagination instanceof HttpApiContracts.PageNumberPagination value) {
                variables.put("pageSize", Integer.toString(value.pageSize()));
            } else if (pagination instanceof HttpApiContracts.OffsetLimitPagination value) {
                variables.put("limit", Integer.toString(value.limit()));
            }
        }
    }

    private static final class PullContext {
        private final HttpApiContracts.RuntimeConnection connection;
        private final HttpApiContracts.ExecutionLimits limits;
        private final Instant startedAt = Instant.now();
        private long responseBytes;
        private long lastRequestAt;

        private PullContext(
                HttpApiContracts.RuntimeConnection connection,
                HttpApiContracts.ExecutionLimits limits
        ) {
            this.connection = connection;
            this.limits = limits;
        }

        private void checkDuration() {
            if (Duration.between(startedAt, Instant.now()).toSeconds() >= limits.maxDurationSeconds()) {
                throw failure("API_MAX_DURATION_EXCEEDED", "API 拉取持续时间超过限制");
            }
        }

        private void checkActive() {
            if (Thread.currentThread().isInterrupted()) {
                InterruptedException interrupted = new InterruptedException("HTTP API pull interrupted");
                throw new HttpApiPullException(
                        "API_REQUEST_INTERRUPTED", "HTTP API 请求已取消", null, interrupted);
            }
            checkDuration();
        }

        private void rateLimit() {
            long wait = connection.configuration().minimumRequestIntervalMs()
                    - (System.currentTimeMillis() - lastRequestAt);
            if (lastRequestAt != 0 && wait > 0) sleep(wait);
            lastRequestAt = System.currentTimeMillis();
        }
    }
}

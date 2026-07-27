package cn.superhuang.data.scalpel.business.datasource.service.http;

import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class GenericHttpApiConnector implements HttpApiConnector {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)}");
    private static final Pattern SENSITIVE_RUNTIME_PARAMETER = Pattern.compile(
            "(?i).*(password|passwd|secret|token|credential|api[-_.]?key|access[-_.]?key|signature).*"
    );
    private static final int ERROR_PREVIEW_LIMIT = 4_000;
    private static final int TOKEN_RESPONSE_LIMIT = 1024 * 1024;

    private final ObjectMapper objectMapper;
    private final Map<String, CachedToken> tokenCache = new ConcurrentHashMap<>();
    private final Object tokenRefreshMonitor = new Object();

    public GenericHttpApiConnector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String type() {
        return HttpApiContracts.GENERIC_CONNECTOR;
    }

    @Override
    public ConnectionProbeResult testConnection(HttpApiContracts.RuntimeConnection connection) {
        long started = System.nanoTime();
        try {
            PullContext context = PullContext.forProbe(connection);
            Map<String, String> variables = baseVariables(connection, List.of());
            MutableRequest request = new MutableRequest(
                    HttpApiContracts.HttpMethod.GET,
                    URI.create(connection.configuration().baseUrl()),
                    new ArrayList<>(),
                    new LinkedHashMap<>(),
                    null
            );
            applyDefaultHeaders(connection, request, variables);
            applyAuthentication(connection, request, variables, false);
            HttpResponse<InputStream> response = send(context, toHttpRequest(connection, request), false);
            byte[] body = readLimited(response.body(), Math.min(TOKEN_RESPONSE_LIMIT,
                    connection.configuration().requestTimeoutMs() * 1024L));
            int status = response.statusCode();
            if (status == 401 || status == 403) {
                return new ConnectionProbeResult(
                        false, "API_AUTHENTICATION_FAILED", "API 鉴权失败", elapsedMs(started), status,
                        contentType(response), failure("API_AUTHENTICATION_FAILED", "HTTP " + status,
                                status, body, connection, variables, null)
                );
            }
            if (status >= 500) {
                return new ConnectionProbeResult(
                        false, "API_REMOTE_SERVER_ERROR", "API 服务返回错误", elapsedMs(started), status,
                        contentType(response), failure("API_REMOTE_SERVER_ERROR", "HTTP " + status,
                                status, body, connection, variables, null)
                );
            }
            return new ConnectionProbeResult(
                    true, "API_CONNECTION_OK", "API 地址可访问且鉴权流程成功", elapsedMs(started), status,
                    contentType(response), null
            );
        } catch (RuntimeException exception) {
            HttpApiExecutionException failure = asExecutionException(exception);
            return new ConnectionProbeResult(
                    false, failure.code(), safeMessage(failure), elapsedMs(started), failure.httpStatus(), null,
                    new HttpApiFailure(failure.code(), safeMessage(failure), failure.httpStatus(),
                            failure.responsePreview(), rootCause(failure))
            );
        }
    }

    @Override
    public PullResult pull(HttpApiContracts.PullRequest request) {
        if (request == null || request.connection() == null || request.resource() == null) {
            throw new HttpApiExecutionException("API_INVALID_CONFIGURATION", "HTTP API 拉取配置不完整");
        }
        PullContext context = new PullContext(request.connection(), request.resource().limits());
        Map<String, String> variables = baseVariables(request.connection(), request.runtimeParameters());
        RequestResponse firstResult = null;
        HttpApiContracts.RequestTemplate resultRequest = request.resource().request();
        if (request.resource().invocationType() == HttpApiContracts.InvocationType.ASYNC_JOB) {
            RequestResponse submission = execute(
                    context, request.connection(), request.resource(), request.resource().request(),
                    variables, new PageState(), null);
            String jobId = requiredText(submission.json().at(request.resource().asyncJob().jobIdPointer()),
                    "API_ASYNC_JOB_ID_MISSING", "异步接口未返回 Job ID");
            variables.put("jobId", jobId);
            waitForAsyncJob(context, request, variables);
            resultRequest = request.resource().asyncJob().resultRequest();
        }

        List<String> columns = request.resource().outputFields().stream()
                .map(HttpApiContracts.OutputField::name).toList();
        List<List<Object>> rows = new ArrayList<>();
        PageState pageState = new PageState();
        HttpApiContracts.PaginationConfiguration pagination = request.resource().pagination();
        pageState.initialize(pagination);
        boolean paginated = pagination != null && pagination.type() != HttpApiContracts.PaginationType.NONE;
        int pageCount = 0;
        String lastContentType = null;
        int lastStatus = 0;
        while (true) {
            context.checkDuration();
            if (++pageCount > context.limits.maxPages()) {
                throw new HttpApiExecutionException("API_MAX_PAGES_EXCEEDED", "API 返回页数超过限制");
            }
            RequestResponse response = firstResult == null
                    ? execute(context, request.connection(), request.resource(), resultRequest, variables,
                            pageState, pagination)
                    : firstResult;
            firstResult = null;
            lastContentType = response.contentType();
            lastStatus = response.status();
            List<JsonNode> records = records(response.json(), request.resource().recordsPointer());
            for (JsonNode record : records) {
                List<Object> row = request.resource().outputFields().stream()
                        .map(field -> outputValue(record, field)).toList();
                rows.add(row);
                if (rows.size() > context.limits.maxRows()) {
                    throw new HttpApiExecutionException("API_MAX_ROWS_EXCEEDED", "API 返回记录数超过限制");
                }
            }
            if (!paginated || !advancePage(
                    pagination, pageState, response.json(), records.size(), request.connection().configuration().baseUrl())) {
                break;
            }
        }
        return new PullResult(columns, rows, pageCount, context.responseBytes, lastStatus, lastContentType);
    }

    private void waitForAsyncJob(
            PullContext context,
            HttpApiContracts.PullRequest pullRequest,
            Map<String, String> variables
    ) {
        HttpApiContracts.AsyncJobConfiguration async = pullRequest.resource().asyncJob();
        Instant pollingDeadline = Instant.now().plusMillis(async.pollingTimeoutMs());
        while (true) {
            context.checkDuration();
            RequestResponse response = execute(
                    context, pullRequest.connection(), pullRequest.resource(), async.statusRequest(),
                    variables, new PageState(), null);
            String status = requiredText(response.json().at(async.statusPointer()),
                    "API_ASYNC_STATUS_MISSING", "异步接口未返回任务状态");
            if (async.successStatuses().contains(status)) {
                return;
            }
            if (async.failureStatuses().contains(status)) {
                throw new HttpApiExecutionException(
                        "API_ASYNC_JOB_FAILED", "远程异步任务执行失败，状态：" + status);
            }
            if (!async.runningStatuses().isEmpty() && !async.runningStatuses().contains(status)) {
                throw new HttpApiExecutionException(
                        "API_ASYNC_STATUS_UNKNOWN", "远程异步任务返回未知状态：" + status);
            }
            if (Instant.now().plusMillis(async.pollingIntervalMs()).isAfter(pollingDeadline)) {
                throw new HttpApiExecutionException("API_ASYNC_TIMEOUT", "等待远程异步任务超时");
            }
            sleep(async.pollingIntervalMs());
        }
    }

    private RequestResponse execute(
            PullContext context,
            HttpApiContracts.RuntimeConnection connection,
            HttpApiContracts.ResourceDefinition resource,
            HttpApiContracts.RequestTemplate template,
            Map<String, String> baseVariables,
            PageState pageState,
            HttpApiContracts.PaginationConfiguration pagination
    ) {
        boolean refreshedAuthentication = false;
        boolean forceTokenRefresh = false;
        int retry = 0;
        while (true) {
            context.checkDuration();
            Map<String, String> variables = new LinkedHashMap<>(baseVariables);
            pageState.addVariables(variables, pagination);
            MutableRequest request = prepare(
                    connection, resource, template, variables, pageState, pagination, forceTokenRefresh);
            forceTokenRefresh = false;
            try {
                context.rateLimit();
                HttpResponse<InputStream> response = send(context, toHttpRequest(connection, request), true);
                int status = response.statusCode();
                long remaining = context.limits.maxResponseBytes() - context.responseBytes;
                byte[] body = readLimited(response.body(), remaining);
                context.responseBytes += body.length;
                if ((status == 401 || status == 403) && dynamicAuthentication(connection)
                        && !refreshedAuthentication) {
                    refreshedAuthentication = true;
                    forceTokenRefresh = true;
                    continue;
                }
                if (retryableStatus(status) && retry < connection.configuration().maxRetries()) {
                    sleep(retryDelay(response, retry++));
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new HttpApiExecutionException(
                            status == 401 || status == 403 ? "API_AUTHENTICATION_FAILED" : "API_HTTP_ERROR",
                            "API 请求失败，HTTP " + status,
                            status,
                            redactedPreview(body, connection, variables),
                            null
                    );
                }
                JsonNode json;
                try {
                    json = body.length == 0 ? objectMapper.createObjectNode() : objectMapper.readTree(body);
                } catch (RuntimeException exception) {
                    throw new HttpApiExecutionException(
                            "API_RESPONSE_INVALID_JSON", "API 响应不是有效的 JSON", status,
                            redactedPreview(body, connection, variables), exception);
                }
                return new RequestResponse(status, contentType(response), json);
            } catch (HttpApiExecutionException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                if (retry++ < connection.configuration().maxRetries()) {
                    sleep(retryDelay(null, retry));
                    continue;
                }
                throw new HttpApiExecutionException(
                        "API_REQUEST_FAILED", safeMessage(exception), null, null, exception);
            }
        }
    }

    private MutableRequest prepare(
            HttpApiContracts.RuntimeConnection connection,
            HttpApiContracts.ResourceDefinition resource,
            HttpApiContracts.RequestTemplate template,
            Map<String, String> variables,
            PageState pageState,
            HttpApiContracts.PaginationConfiguration pagination,
            boolean forceTokenRefresh
    ) {
        String renderedPath = render(template.path(), variables);
        URI uri = pageState.nextUrl == null
                ? combine(connection.configuration().baseUrl(), renderedPath)
                : pageState.nextUrl;
        List<HttpApiContracts.NamedValue> query = new ArrayList<>();
        if (pageState.nextUrl == null) {
            for (HttpApiContracts.NamedValue value : template.queryParameters()) {
                query.add(new HttpApiContracts.NamedValue(value.name(), render(value.value(), variables)));
            }
        }
        Map<String, String> headers = new LinkedHashMap<>();
        String body = render(template.bodyTemplate(), variables);
        MutableRequest request = new MutableRequest(template.method(), uri, query, headers, body);
        applyDefaultHeaders(connection, request, variables);
        for (HttpApiContracts.NamedValue value : template.headers()) {
            request.headers.put(value.name(), render(value.value(), variables));
        }
        applyPagination(pagination, request, pageState);
        applyAuthentication(connection, request, variables, forceTokenRefresh);
        applySigning(connection, resource.signing(), request, variables);
        return request;
    }

    private void applyDefaultHeaders(
            HttpApiContracts.RuntimeConnection connection,
            MutableRequest request,
            Map<String, String> variables
    ) {
        for (HttpApiContracts.NamedValue value : connection.configuration().defaultHeaders()) {
            request.headers.put(value.name(), render(value.value(), variables));
        }
    }

    private void applyAuthentication(
            HttpApiContracts.RuntimeConnection connection,
            MutableRequest request,
            Map<String, String> variables,
            boolean forceTokenRefresh
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
            case HttpApiContracts.ApiKeyAuthentication apiKey -> {
                variables.put("credential.apiKey", credentials.apiKey());
                addValue(request, apiKey.location(), apiKey.name(), render(apiKey.valueTemplate(), variables));
            }
            case HttpApiContracts.OAuth2ClientCredentialsAuthentication oauth -> {
                String token = tokenFor(connection, variables, forceTokenRefresh);
                variables.put("token", token);
                addValue(request, oauth.tokenLocation(), oauth.tokenName(), render(oauth.tokenValueTemplate(), variables));
            }
            case HttpApiContracts.TokenEndpointAuthentication tokenEndpoint -> {
                String token = tokenFor(connection, variables, forceTokenRefresh);
                variables.put("token", token);
                addValue(request, tokenEndpoint.tokenLocation(), tokenEndpoint.tokenName(),
                        render(tokenEndpoint.tokenValueTemplate(), variables));
            }
        }
    }

    private String tokenFor(
            HttpApiContracts.RuntimeConnection connection,
            Map<String, String> variables,
            boolean forceRefresh
    ) {
        String key = tokenCacheKey(connection, variables);
        if (forceRefresh) {
            tokenCache.remove(key);
        }
        CachedToken cached = tokenCache.get(key);
        if (cached != null && cached.valid()) {
            return cached.value();
        }
        synchronized (tokenRefreshMonitor) {
            cached = tokenCache.get(key);
            if (cached != null && cached.valid()) {
                return cached.value();
            }
            CachedToken refreshed = acquireToken(connection, variables);
            tokenCache.put(key, refreshed);
            return refreshed.value();
        }
    }

    private CachedToken acquireToken(
            HttpApiContracts.RuntimeConnection connection,
            Map<String, String> inheritedVariables
    ) {
        return switch (connection.configuration().authentication()) {
            case HttpApiContracts.OAuth2ClientCredentialsAuthentication oauth ->
                    acquireOAuthToken(connection, oauth);
            case HttpApiContracts.TokenEndpointAuthentication endpoint ->
                    acquireEndpointToken(connection, endpoint, inheritedVariables);
            default -> throw new HttpApiExecutionException(
                    "API_AUTHENTICATION_CONFIGURATION_INVALID", "当前鉴权方式不生成运行时 Token");
        };
    }

    private CachedToken acquireOAuthToken(
            HttpApiContracts.RuntimeConnection connection,
            HttpApiContracts.OAuth2ClientCredentialsAuthentication oauth
    ) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "client_credentials");
        form.put("client_id", oauth.clientId());
        form.put("client_secret", connection.credentials().clientSecret());
        if (!oauth.scopes().isEmpty()) {
            form.put("scope", String.join(" ", oauth.scopes()));
        }
        if (oauth.audience() != null && !oauth.audience().isBlank()) {
            form.put("audience", oauth.audience());
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(oauth.tokenUrl()))
                .timeout(Duration.ofMillis(connection.configuration().requestTimeoutMs()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(formEncoded(form)))
                .build();
        JsonNode response = tokenRequest(connection, request);
        String token = requiredText(response.at("/access_token"),
                "API_TOKEN_MISSING", "OAuth2 响应未返回 access_token");
        long ttl = longValue(response.at("/expires_in"), 300);
        return cachedToken(token, ttl);
    }

    private CachedToken acquireEndpointToken(
            HttpApiContracts.RuntimeConnection connection,
            HttpApiContracts.TokenEndpointAuthentication endpoint,
            Map<String, String> inheritedVariables
    ) {
        Map<String, String> variables = new LinkedHashMap<>(inheritedVariables);
        variables.put("credential.username", nullToEmpty(endpoint.username()));
        variables.put("credential.password", nullToEmpty(connection.credentials().tokenEndpointPassword()));
        variables.put("credential.tokenEndpointPassword",
                nullToEmpty(connection.credentials().tokenEndpointPassword()));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(endpoint.tokenUrl()))
                .timeout(Duration.ofMillis(connection.configuration().requestTimeoutMs()))
                .header("Accept", "application/json");
        for (HttpApiContracts.NamedValue header : endpoint.headers()) {
            builder.header(header.name(), render(header.value(), variables));
        }
        if (endpoint.method() == HttpApiContracts.HttpMethod.POST) {
            String body = render(endpoint.bodyTemplate(), variables);
            if (builder.build().headers().firstValue("Content-Type").isEmpty()) {
                builder.header("Content-Type", body != null && body.stripLeading().startsWith("{")
                        ? "application/json" : "application/x-www-form-urlencoded");
            }
            builder.POST(HttpRequest.BodyPublishers.ofString(nullToEmpty(body)));
        } else {
            builder.GET();
        }
        JsonNode response = tokenRequest(connection, builder.build());
        String token = requiredText(response.at(endpoint.tokenPointer()),
                "API_TOKEN_MISSING", "Token Endpoint 响应未返回 Token");
        long ttl = endpoint.expiresInPointer() == null
                ? endpoint.fixedTtlSeconds()
                : longValue(response.at(endpoint.expiresInPointer()), endpoint.fixedTtlSeconds() == null
                ? 300 : endpoint.fixedTtlSeconds());
        return cachedToken(token, ttl);
    }

    private JsonNode tokenRequest(HttpApiContracts.RuntimeConnection connection, HttpRequest request) {
        try {
            HttpResponse<InputStream> response = client(connection).send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            byte[] body = readLimited(response.body(), TOKEN_RESPONSE_LIMIT);
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new HttpApiExecutionException(
                        "API_TOKEN_REQUEST_FAILED", "运行时 Token 获取失败，HTTP " + response.statusCode(),
                        response.statusCode(), redactedPreview(body, connection, Map.of()), null);
            }
            try {
                return objectMapper.readTree(body);
            } catch (RuntimeException exception) {
                throw new HttpApiExecutionException(
                        "API_TOKEN_RESPONSE_INVALID", "Token 接口响应不是有效的 JSON",
                        response.statusCode(), redactedPreview(body, connection, Map.of()), exception);
            }
        } catch (HttpApiExecutionException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new HttpApiExecutionException(
                    "API_TOKEN_REQUEST_FAILED", "运行时 Token 获取失败", null, null, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HttpApiExecutionException("API_REQUEST_INTERRUPTED", "HTTP API 请求已取消", null, null, exception);
        }
    }

    private void applyPagination(
            HttpApiContracts.PaginationConfiguration pagination,
            MutableRequest request,
            PageState state
    ) {
        if (pagination == null) {
            return;
        }
        switch (pagination) {
            case HttpApiContracts.NoPagination ignored -> {
            }
            case HttpApiContracts.PageNumberPagination page -> {
                int current = state.page == null ? page.initialPage() : state.page;
                state.page = current;
                addValue(request, page.location(), page.pageParameter(), Integer.toString(current));
                addValue(request, page.location(), page.pageSizeParameter(), Integer.toString(page.pageSize()));
            }
            case HttpApiContracts.OffsetLimitPagination offset -> {
                int current = state.offset == null ? offset.initialOffset() : state.offset;
                state.offset = current;
                addValue(request, offset.location(), offset.offsetParameter(), Integer.toString(current));
                addValue(request, offset.location(), offset.limitParameter(), Integer.toString(offset.limit()));
            }
            case HttpApiContracts.CursorPagination cursor -> {
                String current = state.cursor == null ? cursor.initialCursor() : state.cursor;
                state.cursor = current;
                if (current != null) {
                    addValue(request, cursor.location(), cursor.cursorParameter(), current);
                }
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
        if (signing == null || signing.type() == HttpApiContracts.SignatureType.NONE) {
            return;
        }
        if (signing.timestamp() != null) {
            long timestamp = signing.timestamp().unit() == HttpApiContracts.TimestampUnit.SECONDS
                    ? Instant.now().getEpochSecond() : System.currentTimeMillis();
            variables.put("timestamp", Long.toString(timestamp));
            addValue(request, signing.timestamp().location(), signing.timestamp().name(), Long.toString(timestamp));
        }
        if (signing.nonce() != null) {
            String nonce = UUID.randomUUID().toString().replace("-", "");
            variables.put("nonce", nonce);
            addValue(request, signing.nonce().location(), signing.nonce().name(), nonce);
        }
        URI unsignedUri = withQuery(request.uri, request.query);
        variables.put("request.method", request.method.name());
        variables.put("request.path", unsignedUri.getRawPath());
        variables.put("request.query", nullToEmpty(unsignedUri.getRawQuery()));
        variables.put("request.body", nullToEmpty(request.body));
        variables.put("request.bodyHash", hex(MessageDigestHolder.sha256(
                nullToEmpty(request.body).getBytes(StandardCharsets.UTF_8)), false));
        variables.put("credential.signingSecret", nullToEmpty(connection.credentials().signingSecret()));
        String canonical = render(signing.canonicalTemplate(), variables);
        byte[] signature = sign(signing.type(), canonical, connection.credentials());
        String encoded = encodeSignature(signature, signing.output().encoding());
        variables.put("signature", encoded);
        String outputValue = signing.output().valueTemplate() == null || signing.output().valueTemplate().isBlank()
                ? encoded : render(signing.output().valueTemplate(), variables);
        addValue(request, signing.output().location(), signing.output().name(), outputValue);
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
                    Signature signature = Signature.getInstance("SHA256withRSA");
                    signature.initSign(privateKey(credentials.signingPrivateKey()));
                    signature.update(bytes);
                    yield signature.sign();
                }
                case NONE -> throw new IllegalArgumentException("NONE does not sign");
            };
        } catch (Exception exception) {
            throw new HttpApiExecutionException(
                    "API_SIGNING_FAILED", "无法生成 API 请求签名", null, null, exception);
        }
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

    private static String encodeSignature(byte[] signature, HttpApiContracts.SignatureEncoding encoding) {
        return switch (encoding) {
            case HEX_LOWERCASE -> hex(signature, false);
            case HEX_UPPERCASE -> hex(signature, true);
            case BASE64 -> Base64.getEncoder().encodeToString(signature);
            case BASE64_URL -> Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        };
    }

    private static boolean advancePage(
            HttpApiContracts.PaginationConfiguration pagination,
            PageState state,
            JsonNode response,
            int recordCount,
            String baseUrl
    ) {
        return switch (pagination) {
            case HttpApiContracts.NoPagination ignored -> false;
            case HttpApiContracts.PageNumberPagination page -> {
                if (recordCount == 0 || falseAt(response, page.hasMorePointer())) {
                    yield false;
                }
                long totalPages = longAt(response, page.totalPagesPointer(), -1);
                long consumedPages = (long) state.page - page.initialPage() + 1;
                if (totalPages >= 0 && consumedPages >= totalPages) {
                    yield false;
                }
                if (totalPages < 0 && !hasText(page.hasMorePointer()) && recordCount < page.pageSize()) {
                    yield false;
                }
                state.page++;
                yield true;
            }
            case HttpApiContracts.OffsetLimitPagination offset -> {
                if (recordCount == 0 || falseAt(response, offset.hasMorePointer())) {
                    yield false;
                }
                long total = longAt(response, offset.totalPointer(), -1);
                if (total >= 0 && (long) state.offset + recordCount >= total) {
                    yield false;
                }
                if (total < 0 && !hasText(offset.hasMorePointer()) && recordCount < offset.limit()) {
                    yield false;
                }
                state.offset += offset.limit();
                yield true;
            }
            case HttpApiContracts.CursorPagination cursor -> {
                if (recordCount == 0 || falseAt(response, cursor.hasMorePointer())) {
                    yield false;
                }
                String next = textAt(response, cursor.nextCursorPointer());
                if (!hasText(next)) {
                    yield false;
                }
                if (!state.seen.add("cursor:" + next)) {
                    throw new HttpApiExecutionException("API_PAGINATION_LOOP", "API 返回了重复 Cursor");
                }
                state.cursor = next;
                yield true;
            }
            case HttpApiContracts.NextUrlPagination nextUrl -> {
                if (recordCount == 0) {
                    yield false;
                }
                String next = textAt(response, nextUrl.nextUrlPointer());
                if (!hasText(next)) {
                    yield false;
                }
                URI resolved = URI.create(baseUrl).resolve(next);
                if (nextUrl.sameOriginOnly() && !sameOrigin(URI.create(baseUrl), resolved)) {
                    throw new HttpApiExecutionException(
                            "API_NEXT_URL_ORIGIN_REJECTED", "API 返回的下一页地址不属于数据源同源地址");
                }
                if (!state.seen.add("url:" + resolved)) {
                    throw new HttpApiExecutionException("API_PAGINATION_LOOP", "API 返回了重复下一页地址");
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
                throw new HttpApiExecutionException(
                        "API_REQUIRED_FIELD_MISSING", "API 响应缺少必填字段：" + field.name());
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
                case DECIMAL -> decimalValue(value, field);
                case STRING -> checkedString(value.isTextual() ? value.textValue() : value.toString(), field);
                case BINARY -> value.isBinary() ? value.binaryValue() : Base64.getDecoder().decode(value.asString());
                case DATE -> LocalDate.parse(value.asString()).toString();
                case TIMESTAMP -> checkedTimestamp(value.asString());
                case TIMESTAMP_NTZ -> LocalDateTime.parse(value.asString()).toString();
                case GEOMETRY -> throw new IllegalArgumentException("HTTP API 字段不支持 Geometry 类型");
            };
        } catch (RuntimeException exception) {
            throw new HttpApiExecutionException(
                    "API_FIELD_CONVERSION_FAILED", "API 字段无法转换：" + field.name(), null, null, exception);
        }
    }

    private static boolean booleanValue(JsonNode value) {
        if (value.isBoolean()) {
            return value.booleanValue();
        }
        String text = value.asString();
        if ("true".equalsIgnoreCase(text)) return true;
        if ("false".equalsIgnoreCase(text)) return false;
        throw new IllegalArgumentException("invalid boolean");
    }

    private static BigDecimal decimalValue(JsonNode value) {
        return new BigDecimal(numberText(value));
    }

    private static BigDecimal decimalValue(JsonNode value, HttpApiContracts.OutputField field) {
        BigDecimal scaled = decimalValue(value).setScale(field.type().scale(), RoundingMode.UNNECESSARY);
        if (scaled.precision() > field.type().precision()) {
            throw new ArithmeticException("decimal precision overflow");
        }
        return scaled;
    }

    private static String numberText(JsonNode value) {
        if (!value.isNumber() && !value.isTextual()) {
            throw new IllegalArgumentException("not a number");
        }
        return value.asString();
    }

    private static String checkedString(String value, HttpApiContracts.OutputField field) {
        if (field.type().length() != null
                && value.codePointCount(0, value.length()) > field.type().length()) {
            throw new IllegalArgumentException("string length overflow");
        }
        return value;
    }

    private static String checkedTimestamp(String value) {
        try {
            Instant.parse(value);
        } catch (RuntimeException ignored) {
            LocalDateTime.parse(value);
        }
        return value;
    }

    private static List<JsonNode> records(JsonNode response, String pointer) {
        JsonNode node = pointer == null || pointer.isEmpty() ? response : response.at(pointer);
        if (node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            return List.of(node);
        }
        List<JsonNode> records = new ArrayList<>();
        node.forEach(records::add);
        return List.copyOf(records);
    }

    private void addValue(
            MutableRequest request,
            HttpApiContracts.ValueLocation location,
            String name,
            String value
    ) {
        switch (location) {
            case HEADER -> request.headers.put(name, value);
            case QUERY -> request.query.add(new HttpApiContracts.NamedValue(name, value));
            case BODY -> request.body = addBodyValue(request.body, name, value);
        }
    }

    private String addBodyValue(String body, String name, String value) {
        try {
            JsonNode parsed = body == null || body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
            if (!(parsed instanceof ObjectNode object)) {
                throw new IllegalArgumentException("body is not an object");
            }
            object.put(name, value);
            return objectMapper.writeValueAsString(object);
        } catch (RuntimeException exception) {
            throw new HttpApiExecutionException(
                    "API_REQUEST_BODY_INVALID", "分页、时间戳或签名写入 Body 时，Body 必须是 JSON 对象", null, null, exception);
        }
    }

    private static Map<String, String> baseVariables(
            HttpApiContracts.RuntimeConnection connection,
            List<HttpApiContracts.RuntimeParameter> runtimeParameters
    ) {
        Map<String, String> variables = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        for (HttpApiContracts.RuntimeParameter parameter : runtimeParameters == null
                ? List.<HttpApiContracts.RuntimeParameter>of() : runtimeParameters) {
            if (parameter == null || !hasText(parameter.name()) || parameter.value() == null
                    || SENSITIVE_RUNTIME_PARAMETER.matcher(parameter.name()).matches()
                    || !names.add(parameter.name())) {
                throw new HttpApiExecutionException(
                        "API_RUNTIME_PARAMETER_INVALID", "API 运行时参数名称为空、重复或值为空");
            }
            variables.put("runtime." + parameter.name(), parameter.value());
        }
        HttpApiContracts.CredentialBundle credentials = connection.credentials();
        variables.put("credential.password", nullToEmpty(credentials.password()));
        variables.put("credential.bearerToken", nullToEmpty(credentials.bearerToken()));
        variables.put("credential.apiKey", nullToEmpty(credentials.apiKey()));
        variables.put("credential.clientSecret", nullToEmpty(credentials.clientSecret()));
        variables.put("credential.tokenEndpointPassword", nullToEmpty(credentials.tokenEndpointPassword()));
        variables.put("credential.signingSecret", nullToEmpty(credentials.signingSecret()));
        switch (connection.configuration().authentication()) {
            case HttpApiContracts.BasicAuthentication basic ->
                    variables.put("credential.username", basic.username());
            case HttpApiContracts.OAuth2ClientCredentialsAuthentication oauth ->
                    variables.put("credential.clientId", oauth.clientId());
            case HttpApiContracts.TokenEndpointAuthentication endpoint ->
                    variables.put("credential.username", nullToEmpty(endpoint.username()));
            default -> {
            }
        }
        return variables;
    }

    private static HttpRequest toHttpRequest(
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
            builder.POST(HttpRequest.BodyPublishers.ofString(nullToEmpty(request.body)));
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private static URI withQuery(URI uri, List<HttpApiContracts.NamedValue> query) {
        StringBuilder value = new StringBuilder(uri.toString());
        boolean hasQuery = uri.getRawQuery() != null;
        for (HttpApiContracts.NamedValue item : query) {
            value.append(hasQuery ? '&' : '?');
            hasQuery = true;
            value.append(urlEncode(item.name())).append('=').append(urlEncode(item.value()));
        }
        return URI.create(value.toString());
    }

    private static URI combine(String baseUrl, String path) {
        return URI.create(baseUrl.replaceFirst("/+$", "") + "/" + path.replaceFirst("^/+", ""));
    }

    private static HttpClient client(HttpApiContracts.RuntimeConnection connection) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connection.configuration().connectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private static HttpResponse<InputStream> send(
            PullContext context,
            HttpRequest request,
            boolean countBytes
    ) {
        try {
            return client(context.connection).send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException exception) {
            throw new HttpApiExecutionException("API_NETWORK_ERROR", "无法连接 HTTP API", null, null, exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HttpApiExecutionException("API_REQUEST_INTERRUPTED", "HTTP API 请求已取消", null, null, exception);
        }
    }

    private static byte[] readLimited(InputStream input, long maxBytes) {
        if (maxBytes < 0) {
            throw new HttpApiExecutionException("API_MAX_RESPONSE_BYTES_EXCEEDED", "API 响应大小超过限制");
        }
        int limit = (int) Math.min(Integer.MAX_VALUE - 1L, maxBytes);
        try (input) {
            byte[] body = input.readNBytes(limit + 1);
            if (body.length > limit) {
                throw new HttpApiExecutionException(
                        "API_MAX_RESPONSE_BYTES_EXCEEDED", "API 响应大小超过限制");
            }
            return body;
        } catch (IOException exception) {
            throw new HttpApiExecutionException("API_RESPONSE_READ_FAILED", "无法读取 API 响应", null, null, exception);
        }
    }

    private static HttpApiFailure failure(
            String code,
            String message,
            Integer status,
            byte[] body,
            HttpApiContracts.RuntimeConnection connection,
            Map<String, String> variables,
            Throwable cause
    ) {
        return new HttpApiFailure(code, message, status, redactedPreview(body, connection, variables), cause);
    }

    private static String redactedPreview(
            byte[] body,
            HttpApiContracts.RuntimeConnection connection,
            Map<String, String> variables
    ) {
        String value = new String(body, StandardCharsets.UTF_8);
        for (String secret : sensitiveValues(connection, variables)) {
            if (hasText(secret)) {
                value = value.replace(secret, "[REDACTED]");
            }
        }
        return value.substring(0, Math.min(ERROR_PREVIEW_LIMIT, value.length()));
    }

    private static List<String> sensitiveValues(
            HttpApiContracts.RuntimeConnection connection,
            Map<String, String> variables
    ) {
        HttpApiContracts.CredentialBundle c = connection.credentials();
        List<String> values = new ArrayList<>(List.of(
                nullToEmpty(c.password()), nullToEmpty(c.bearerToken()), nullToEmpty(c.apiKey()),
                nullToEmpty(c.clientSecret()), nullToEmpty(c.tokenEndpointPassword()),
                nullToEmpty(c.signingSecret()), nullToEmpty(c.signingPrivateKey())
        ));
        values.add(nullToEmpty(variables.get("token")));
        values.add(nullToEmpty(variables.get("signature")));
        return values;
    }

    private static String render(String template, Map<String, String> variables) {
        if (template == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String value = variables.get(matcher.group(1));
            if (value == null) {
                throw new HttpApiExecutionException(
                        "API_TEMPLATE_VALUE_MISSING", "API 请求缺少模板参数：" + matcher.group(1));
            }
            matcher.appendReplacement(output, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String tokenCacheKey(
            HttpApiContracts.RuntimeConnection connection,
            Map<String, String> variables
    ) {
        String material = connection.configuration().baseUrl()
                + "|" + connection.configuration().authentication()
                + "|" + connection.credentials().clientSecret()
                + "|" + connection.credentials().tokenEndpointPassword()
                + "|" + variables.entrySet().stream().filter(entry -> entry.getKey().startsWith("runtime."))
                .sorted(Map.Entry.comparingByKey()).toList();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigestHolder.sha256(
                material.getBytes(StandardCharsets.UTF_8)));
    }

    private void invalidateToken(
            HttpApiContracts.RuntimeConnection connection,
            Map<String, String> variables
    ) {
        tokenCache.remove(tokenCacheKey(connection, variables));
    }

    private static boolean dynamicAuthentication(HttpApiContracts.RuntimeConnection connection) {
        return connection.configuration().authentication() instanceof HttpApiContracts.OAuth2ClientCredentialsAuthentication
                || connection.configuration().authentication() instanceof HttpApiContracts.TokenEndpointAuthentication;
    }

    private static CachedToken cachedToken(String token, long ttlSeconds) {
        long safeTtl = Math.max(5, ttlSeconds - Math.min(60, Math.max(1, ttlSeconds / 10)));
        return new CachedToken(token, Instant.now().plusSeconds(safeTtl));
    }

    private static long longValue(JsonNode node, long defaultValue) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return defaultValue;
        }
        try {
            return node.isNumber() ? node.longValue() : Long.parseLong(node.asString());
        } catch (RuntimeException exception) {
            return defaultValue;
        }
    }

    private static String requiredText(JsonNode node, String code, String message) {
        String value = node == null || node.isMissingNode() || node.isNull() ? null : node.asString();
        if (!hasText(value)) {
            throw new HttpApiExecutionException(code, message);
        }
        return value;
    }

    private static String textAt(JsonNode node, String pointer) {
        if (!hasText(pointer)) {
            return null;
        }
        JsonNode value = node.at(pointer);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private static boolean falseAt(JsonNode node, String pointer) {
        if (!hasText(pointer)) {
            return false;
        }
        JsonNode value = node.at(pointer);
        return !value.isMissingNode() && !value.isNull()
                && (value.isBoolean() ? !value.booleanValue() : "false".equalsIgnoreCase(value.asString()));
    }

    private static long longAt(JsonNode node, String pointer, long defaultValue) {
        return hasText(pointer) ? longValue(node.at(pointer), defaultValue) : defaultValue;
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static boolean retryableStatus(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    private static long retryDelay(HttpResponse<?> response, int retry) {
        if (response != null) {
            String value = response.headers().firstValue("Retry-After").orElse(null);
            if (value != null) {
                try {
                    return Math.min(30_000, Math.max(0, Long.parseLong(value) * 1000));
                } catch (NumberFormatException ignored) {
                    // Fall through to bounded exponential backoff.
                }
            }
        }
        return Math.min(2_000, 100L << Math.min(5, Math.max(0, retry)));
    }

    private static String formEncoded(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String contentType(HttpResponse<?> response) {
        return response.headers().firstValue("Content-Type").orElse(null);
    }

    private static String hex(byte[] value, boolean uppercase) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format(uppercase ? "%02X" : "%02x", item));
        }
        return result.toString();
    }

    private static String safeMessage(Throwable exception) {
        String value = exception.getMessage();
        return value == null || value.isBlank() ? exception.getClass().getSimpleName() : value;
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static HttpApiExecutionException asExecutionException(RuntimeException exception) {
        return exception instanceof HttpApiExecutionException api ? api : new HttpApiExecutionException(
                "API_CONNECTION_FAILED", safeMessage(exception), null, null, exception);
    }

    private static long elapsedMs(long started) {
        return Math.max(0, (System.nanoTime() - started) / 1_000_000L);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new HttpApiExecutionException("API_REQUEST_INTERRUPTED", "HTTP API 请求已取消", null, null, exception);
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record RequestResponse(int status, String contentType, JsonNode json) {
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

        private static PullContext forProbe(HttpApiContracts.RuntimeConnection connection) {
            return new PullContext(connection, new HttpApiContracts.ExecutionLimits(
                    1, 1, TOKEN_RESPONSE_LIMIT, Math.max(1, connection.configuration().requestTimeoutMs() / 1000)));
        }

        private void checkDuration() {
            if (Duration.between(startedAt, Instant.now()).toSeconds() >= limits.maxDurationSeconds()) {
                throw new HttpApiExecutionException("API_MAX_DURATION_EXCEEDED", "API 拉取持续时间超过限制");
            }
        }

        private void rateLimit() {
            int interval = connection.configuration().minimumRequestIntervalMs();
            long wait = lastRequestAt == 0 ? 0 : interval - (System.currentTimeMillis() - lastRequestAt);
            if (wait > 0) {
                sleep(wait);
            }
            lastRequestAt = System.currentTimeMillis();
        }
    }

    private static final class MessageDigestHolder {
        private MessageDigestHolder() {
        }

        private static byte[] sha256(byte[] value) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(value);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }
    }
}

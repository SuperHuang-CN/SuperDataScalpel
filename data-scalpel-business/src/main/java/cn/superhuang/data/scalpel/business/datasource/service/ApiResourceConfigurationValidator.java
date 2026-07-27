package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JsonPointer;

import java.net.URI;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ApiResourceConfigurationValidator {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^{}]+)}");
    private static final Pattern RUNTIME_NAME = Pattern.compile("runtime\\.[A-Za-z][A-Za-z0-9_.-]{0,127}");
    private static final Set<String> FIXED_PLACEHOLDERS = Set.of(
            "token", "signature", "timestamp", "nonce", "jobId", "page", "pageSize", "offset", "limit", "cursor",
            "credential.username", "credential.password", "credential.bearerToken", "credential.apiKey",
            "credential.clientId", "credential.clientSecret", "credential.tokenEndpointPassword",
            "credential.signingSecret", "request.method", "request.path", "request.query", "request.body",
            "request.bodyHash"
    );

    public void validate(DataSource dataSource, HttpApiContracts.ResourceDefinition definition) {
        if (dataSource.getType().connectionKind() != DataSourceConnectionKind.HTTP_API) {
            throw badRequest("API 资源只能创建在 HTTP API 数据源下");
        }
        if (!HttpApiContracts.GENERIC_CONNECTOR.equals(definition.connectorType())) {
            throw badRequest("当前未注册 API 连接器：" + definition.connectorType());
        }
        validateRequest(definition.request(), "request");
        validateInvocation(definition);
        validateSigning(dataSource, definition);
        validatePointer(definition.recordsPointer(), "recordsPointer", true);
        validateOutputFields(definition.outputFields());
        validateLimits(definition.limits());
    }

    private static void validateRequest(HttpApiContracts.RequestTemplate request, String path) {
        if (request == null || request.method() == null || !hasText(request.path())) {
            throw badRequest(path + " 请求配置不完整");
        }
        String requestPath = request.path().trim();
        try {
            URI uri = URI.create(requestPath);
            if (uri.isAbsolute() || !requestPath.startsWith("/")) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException exception) {
            throw badRequest(path + ".path 必须是以 / 开头的相对地址");
        }
        validateTemplate(requestPath, path + ".path");
        validateNamedValues(request.queryParameters(), false, path + ".queryParameters");
        validateNamedValues(request.headers(), true, path + ".headers");
        if (request.method() == HttpApiContracts.HttpMethod.GET && hasText(request.bodyTemplate())) {
            throw badRequest(path + " 的 GET 请求不能配置 Body");
        }
        validateTemplate(request.bodyTemplate(), path + ".bodyTemplate");
    }

    private static void validateNamedValues(
            List<HttpApiContracts.NamedValue> values,
            boolean headers,
            String path
    ) {
        Set<String> names = new HashSet<>();
        for (HttpApiContracts.NamedValue item : values == null ? List.<HttpApiContracts.NamedValue>of() : values) {
            if (item == null || !hasText(item.name()) || item.value() == null) {
                throw badRequest(path + " 存在空名称或空值");
            }
            String name = item.name().trim();
            if (headers && !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
                throw badRequest(path + " Header 名称不合法：" + name);
            }
            if (name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0
                    || item.value().indexOf('\r') >= 0 || item.value().indexOf('\n') >= 0) {
                throw badRequest(path + " 不允许换行符");
            }
            String key = headers ? name.toLowerCase(Locale.ROOT) : name;
            if (!names.add(key)) {
                throw badRequest(path + " 参数名称重复：" + name);
            }
            validateTemplate(item.value(), path + "." + name);
        }
    }

    private static void validateSigning(DataSource dataSource, HttpApiContracts.ResourceDefinition definition) {
        HttpApiContracts.SigningConfiguration actual = definition.signing() == null
                ? HttpApiContracts.SigningConfiguration.none() : definition.signing();
        if (actual.type() == null) {
            throw badRequest("签名类型不能为空");
        }
        if (actual.type() == HttpApiContracts.SignatureType.NONE) {
            return;
        }
        if (!hasText(actual.canonicalTemplate()) || actual.output() == null
                || !hasText(actual.output().name()) || actual.output().location() == null
                || actual.output().encoding() == null) {
            throw badRequest("签名原文和输出配置不能为空");
        }
        validateTemplate(actual.canonicalTemplate(), "signing.canonicalTemplate");
        validateTemplate(actual.output().valueTemplate(), "signing.output.valueTemplate");
        validateGeneratedValue(actual.output().name(), actual.output().location(), "签名输出", definition);
        HttpApiContracts.ConnectionConfiguration connection = HttpApiConfigurationCodec.readConnectionConfiguration(
                dataSource.getConnection().apiConfigurationValue());
        if (actual.type() == HttpApiContracts.SignatureType.RSA_SHA256) {
            if (!connection.signingPrivateKeyConfigured()) {
                throw badRequest("RSA_SHA256 需要在数据源中配置签名私钥");
            }
        } else if (!connection.signingSecretConfigured()) {
            throw badRequest(actual.type() + " 需要在数据源中配置签名密钥");
        }
        if (actual.timestamp() != null && actual.timestamp().unit() == null) {
            throw badRequest("时间戳单位不能为空");
        }
        validateGeneratedValue(actual.timestamp() == null ? null : actual.timestamp().name(),
                actual.timestamp() == null ? null : actual.timestamp().location(), "时间戳", definition);
        validateGeneratedValue(actual.nonce() == null ? null : actual.nonce().name(),
                actual.nonce() == null ? null : actual.nonce().location(), "Nonce", definition);
    }

    private static void validateGeneratedValue(
            String name,
            HttpApiContracts.ValueLocation location,
            String label,
            HttpApiContracts.ResourceDefinition definition
    ) {
        if ((name == null) != (location == null)) {
            throw badRequest(label + "名称和位置必须同时配置");
        }
        if (name == null) {
            return;
        }
        if (!hasText(name) || name.length() > 128 || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
            throw badRequest(label + "名称不合法");
        }
        if (location == HttpApiContracts.ValueLocation.HEADER
                && !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
            throw badRequest(label + " Header 名称不合法");
        }
        if (location == HttpApiContracts.ValueLocation.BODY && requests(definition).stream()
                .anyMatch(request -> request.method() != HttpApiContracts.HttpMethod.POST)) {
            throw badRequest(label + "位于 Body 时，所有提交、状态和结果请求都必须使用 POST");
        }
    }

    private static List<HttpApiContracts.RequestTemplate> requests(
            HttpApiContracts.ResourceDefinition definition
    ) {
        if (definition.invocationType() != HttpApiContracts.InvocationType.ASYNC_JOB
                || definition.asyncJob() == null) {
            return List.of(definition.request());
        }
        return List.of(
                definition.request(), definition.asyncJob().statusRequest(), definition.asyncJob().resultRequest());
    }

    private static void validateInvocation(HttpApiContracts.ResourceDefinition definition) {
        HttpApiContracts.PaginationConfiguration pagination = definition.pagination() == null
                ? new HttpApiContracts.NoPagination() : definition.pagination();
        switch (definition.invocationType()) {
            case SINGLE_REQUEST -> {
                if (pagination.type() != HttpApiContracts.PaginationType.NONE || definition.asyncJob() != null) {
                    throw badRequest("单次请求不能配置分页或异步任务");
                }
            }
            case PAGINATED_REQUEST -> {
                if (pagination.type() == HttpApiContracts.PaginationType.NONE || definition.asyncJob() != null) {
                    throw badRequest("分页请求必须配置分页策略且不能配置异步任务");
                }
                validatePagination(pagination, definition.request());
            }
            case ASYNC_JOB -> {
                validateAsyncJob(definition.asyncJob());
                validatePagination(pagination, definition.asyncJob().resultRequest());
            }
        }
    }

    private static void validatePagination(
            HttpApiContracts.PaginationConfiguration pagination,
            HttpApiContracts.RequestTemplate request
    ) {
        switch (pagination) {
            case HttpApiContracts.NoPagination ignored -> {
            }
            case HttpApiContracts.PageNumberPagination page -> {
                validatePaginationLocation(page.location(), request, "页码分页");
                requireNames("页码分页", page.pageParameter(), page.pageSizeParameter());
                if (page.pageSize() < 1 || page.pageSize() > 100_000 || page.initialPage() < 0) {
                    throw badRequest("页码分页参数超出范围");
                }
                optionalPointer(page.hasMorePointer(), "pagination.hasMorePointer");
                optionalPointer(page.totalPagesPointer(), "pagination.totalPagesPointer");
            }
            case HttpApiContracts.OffsetLimitPagination offset -> {
                validatePaginationLocation(offset.location(), request, "Offset 分页");
                requireNames("Offset 分页", offset.offsetParameter(), offset.limitParameter());
                if (offset.limit() < 1 || offset.limit() > 100_000 || offset.initialOffset() < 0) {
                    throw badRequest("Offset 分页参数超出范围");
                }
                optionalPointer(offset.hasMorePointer(), "pagination.hasMorePointer");
                optionalPointer(offset.totalPointer(), "pagination.totalPointer");
            }
            case HttpApiContracts.CursorPagination cursor -> {
                validatePaginationLocation(cursor.location(), request, "Cursor 分页");
                requireNames("Cursor 分页", cursor.cursorParameter());
                validatePointer(cursor.nextCursorPointer(), "pagination.nextCursorPointer", false);
                optionalPointer(cursor.hasMorePointer(), "pagination.hasMorePointer");
            }
            case HttpApiContracts.NextUrlPagination next ->
                    validatePointer(next.nextUrlPointer(), "pagination.nextUrlPointer", false);
        }
    }

    private static void validatePaginationLocation(
            HttpApiContracts.ValueLocation location,
            HttpApiContracts.RequestTemplate request,
            String label
    ) {
        if (location != HttpApiContracts.ValueLocation.QUERY && location != HttpApiContracts.ValueLocation.BODY) {
            throw badRequest(label + "参数只能位于 Query 或 Body");
        }
        if (location == HttpApiContracts.ValueLocation.BODY && request.method() != HttpApiContracts.HttpMethod.POST) {
            throw badRequest(label + "使用 Body 参数时请求方法必须是 POST");
        }
    }

    private static void validateAsyncJob(HttpApiContracts.AsyncJobConfiguration asyncJob) {
        if (asyncJob == null) {
            throw badRequest("异步任务配置不能为空");
        }
        validateRequest(asyncJob.statusRequest(), "asyncJob.statusRequest");
        validateRequest(asyncJob.resultRequest(), "asyncJob.resultRequest");
        validatePointer(asyncJob.jobIdPointer(), "asyncJob.jobIdPointer", false);
        validatePointer(asyncJob.statusPointer(), "asyncJob.statusPointer", false);
        if (asyncJob.successStatuses().isEmpty() || asyncJob.failureStatuses().isEmpty()) {
            throw badRequest("异步任务必须配置成功和失败状态");
        }
        Set<String> all = new HashSet<>();
        for (Set<String> statuses : List.of(
                asyncJob.runningStatuses(), asyncJob.successStatuses(), asyncJob.failureStatuses())) {
            for (String status : statuses) {
                if (!hasText(status) || !all.add(status)) {
                    throw badRequest("异步任务状态不能为空或重复");
                }
            }
        }
        if (asyncJob.pollingIntervalMs() < 100 || asyncJob.pollingIntervalMs() > 60_000
                || asyncJob.pollingTimeoutMs() < asyncJob.pollingIntervalMs()
                || asyncJob.pollingTimeoutMs() > 86_400_000) {
            throw badRequest("异步任务轮询间隔或超时时间超出范围");
        }
    }

    private static void validateOutputFields(List<HttpApiContracts.OutputField> fields) {
        if (fields == null || fields.isEmpty()) {
            throw badRequest("输出 Schema 至少需要一个字段");
        }
        Set<String> names = new HashSet<>();
        for (HttpApiContracts.OutputField field : fields) {
            if (field == null || !hasText(field.name()) || field.type() == null
                    || !field.name().matches("[A-Za-z_][A-Za-z0-9_]{0,127}")) {
                throw badRequest("输出字段名称或类型无效");
            }
            if (!names.add(field.name().toLowerCase(Locale.ROOT))) {
                throw badRequest("输出字段名称重复：" + field.name());
            }
            validatePointer(field.jsonPointer(), "outputFields." + field.name() + ".jsonPointer", true);
        }
    }

    private static void validateLimits(HttpApiContracts.ExecutionLimits limits) {
        if (limits == null || limits.maxPages() < 1 || limits.maxPages() > 100_000
                || limits.maxRows() < 1 || limits.maxRows() > 100_000_000L
                || limits.maxResponseBytes() < 1 || limits.maxResponseBytes() > 10L * 1024 * 1024 * 1024
                || limits.maxDurationSeconds() < 1 || limits.maxDurationSeconds() > 86_400) {
            throw badRequest("API 执行限制超出允许范围");
        }
    }

    private static void validateTemplate(String value, String path) {
        if (value == null) {
            return;
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        int placeholderCount = 0;
        while (matcher.find()) {
            placeholderCount++;
            String name = matcher.group(1);
            if (!FIXED_PLACEHOLDERS.contains(name) && !RUNTIME_NAME.matcher(name).matches()) {
                throw badRequest(path + " 包含不允许的占位符：" + name);
            }
        }
        int markerCount = value.split("\\$\\{", -1).length - 1;
        if (markerCount != placeholderCount) {
            throw badRequest(path + " 包含未闭合的占位符");
        }
    }

    private static void validatePointer(String pointer, String path, boolean allowEmpty) {
        if (pointer == null || (!allowEmpty && pointer.isBlank())) {
            throw badRequest(path + " 不能为空");
        }
        try {
            JsonPointer.compile(pointer);
        } catch (RuntimeException exception) {
            throw badRequest(path + " 必须是合法 JSON Pointer");
        }
    }

    private static void optionalPointer(String pointer, String path) {
        if (hasText(pointer)) {
            validatePointer(pointer, path, false);
        }
    }

    private static void requireNames(String label, String... names) {
        for (String name : names) {
            if (!hasText(name)) {
                throw badRequest(label + "参数名称不能为空");
            }
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}

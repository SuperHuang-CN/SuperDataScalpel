package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.ConnectionOptionsConverter;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.repository.ApiResourceRepository;
import cn.superhuang.data.scalpel.business.datasource.repository.SpatialFeatureResourceRepository;
import cn.superhuang.data.scalpel.business.datasource.web.request.CreateDataSourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.DataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.JdbcDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.KafkaDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.S3DataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.HttpApiDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.TestDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.UpdateDataSourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.ConnectionTestResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.NamespaceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableListResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableMetadataResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TablePreviewResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.KafkaTopicResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.JdbcQueryInspectionResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TdEngineTmqTopicDetailResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TdEngineTmqTopicResponse;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.service.ServiceEngineDataSourceRegistrationService;
import cn.superhuang.data.scalpel.business.service.repository.ScriptDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.repository.SqlDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskDataSourceReferenceRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskResourceBindingRepository;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.httpapi.HttpApiContracts;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.net.URI;

@Service
public class DataSourceService {

    private final DataSourceRepository repository;
    private final SearchEngine searchEngine;
    private final DirectoryService directoryService;
    private final DataSourceRuntimeService runtimeService;
    private final DataModelRepository dataModelRepository;
    private final ServiceEngineDataSourceRegistrationService engineDataSourceRegistrationService;
    private final SqlDataServiceDefinitionRepository sqlServiceDefinitionRepository;
    private final ScriptDataServiceDefinitionRepository scriptServiceDefinitionRepository;
    private final DataSourceCredentialCipher credentialCipher;
    private final ApiResourceRepository apiResourceRepository;
    private final SpatialFeatureResourceRepository spatialFeatureResourceRepository;
    private final TaskDataSourceReferenceRepository taskDataSourceReferenceRepository;
    private final SparkJarTaskResourceBindingRepository sparkJarBindingRepository;

    public DataSourceService(
            DataSourceRepository repository,
            SearchEngine searchEngine,
            DirectoryService directoryService,
            DataSourceRuntimeService runtimeService,
            DataModelRepository dataModelRepository,
            ServiceEngineDataSourceRegistrationService engineDataSourceRegistrationService,
            SqlDataServiceDefinitionRepository sqlServiceDefinitionRepository,
            ScriptDataServiceDefinitionRepository scriptServiceDefinitionRepository,
            DataSourceCredentialCipher credentialCipher,
            ApiResourceRepository apiResourceRepository,
            SpatialFeatureResourceRepository spatialFeatureResourceRepository,
            TaskDataSourceReferenceRepository taskDataSourceReferenceRepository,
            SparkJarTaskResourceBindingRepository sparkJarBindingRepository
    ) {
        this.repository = repository;
        this.searchEngine = searchEngine;
        this.directoryService = directoryService;
        this.runtimeService = runtimeService;
        this.dataModelRepository = dataModelRepository;
        this.engineDataSourceRegistrationService = engineDataSourceRegistrationService;
        this.sqlServiceDefinitionRepository = sqlServiceDefinitionRepository;
        this.scriptServiceDefinitionRepository = scriptServiceDefinitionRepository;
        this.credentialCipher = credentialCipher;
        this.apiResourceRepository = apiResourceRepository;
        this.spatialFeatureResourceRepository = spatialFeatureResourceRepository;
        this.taskDataSourceReferenceRepository = taskDataSourceReferenceRepository;
        this.sparkJarBindingRepository = sparkJarBindingRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<DataSourceResponse> search(SearchRequest request) {
        Page<DataSource> result = searchEngine.search(request, DataSource.class, repository);
        return new PageResponse<>(
                result.getContent().stream().map(DataSourceResponse::from).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public DataSourceResponse get(UUID id) {
        return DataSourceResponse.from(requireDataSource(id));
    }

    @Transactional
    public DataSourceResponse create(CreateDataSourceRequest request) {
        String code = normalizeCode(request.code());
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源编码已存在");
        }
        validateConfiguration(request.type(), request.purposes(), request.connection());
        directoryService.validateAssignment(DirectoryScope.DATA_SOURCE, request.directoryId());
        DataSource dataSource = DataSource.create(
                code,
                request.name(),
                request.directoryId(),
                request.purposes(),
                request.type(),
                request.enabled() == null || request.enabled(),
                request.description(),
                connectionForCreate(request.type(), request.connection())
        );
        return DataSourceResponse.from(repository.saveAndFlush(dataSource));
    }

    @Transactional
    public DataSourceResponse update(UUID id, UpdateDataSourceRequest request) {
        DataSource dataSource = requireDataSource(id);
        String runtimeSignature = engineDataSourceRegistrationService.runtimeSignature(dataSource);
        engineDataSourceRegistrationService.assertCanChangeRuntimeCapability(
                dataSource, request.type(), request.purposes(), request.enabled()
        );
        validateConfiguration(request.type(), request.purposes(), request.connection());
        directoryService.validateAssignment(DirectoryScope.DATA_SOURCE, request.directoryId());
        dataSource.update(
                request.name(),
                request.directoryId(),
                request.purposes(),
                request.type(),
                request.enabled(),
                request.description(),
                connectionForUpdate(dataSource, request.type(), request.connection())
        );
        DataSource saved = repository.saveAndFlush(dataSource);
        engineDataSourceRegistrationService.markOutdatedIfRuntimeSignatureChanged(id, runtimeSignature, saved);
        return DataSourceResponse.from(saved);
    }

    @Transactional
    public void delete(UUID id) {
        requireDataSource(id);
        engineDataSourceRegistrationService.assertDataSourceDeletable(id);
        if (apiResourceRepository.existsByDataSourceId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源下存在 API 资源，不能删除");
        }
        if (spatialFeatureResourceRepository.existsByDataSourceId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源下存在空间要素资源，不能删除");
        }
        if (dataModelRepository.existsByStorageDataSourceId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源已被模型使用，不能删除");
        }
        if (sqlServiceDefinitionRepository.existsByDataSourceId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源已被 SQL 服务使用，不能删除");
        }
        if (scriptServiceDefinitionRepository.existsByDataSourceId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源已被脚本服务使用，不能删除");
        }
        if (taskDataSourceReferenceRepository.existsByDataSourceId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源已被任务直接使用，不能删除");
        }
        if (sparkJarBindingRepository.existsByResourceTypeAndResourceId(
                SparkJarResourceType.JDBC_DATA_SOURCE, id)
                || sparkJarBindingRepository.existsByResourceTypeAndResourceId(
                SparkJarResourceType.KAFKA_TOPIC, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源已被 Spark JAR 任务绑定，不能删除");
        }
        repository.deleteById(id);
    }

    public ConnectionTestResponse test(TestDataSourceConnectionRequest request) {
        return runtimeService.test(request);
    }

    public ConnectionTestResponse test(UUID id) {
        return runtimeService.test(id);
    }

    public java.util.List<NamespaceResponse> listNamespaces(UUID id) {
        return runtimeService.listNamespaces(id);
    }

    public TableListResponse listTables(
            UUID id,
            String catalog,
            String schema,
            String keyword,
            boolean includeViews
    ) {
        return runtimeService.listTables(id, catalog, schema, keyword, includeViews);
    }

    public TableMetadataResponse readTable(UUID id, String catalog, String schema, String table) {
        return runtimeService.readTable(id, catalog, schema, table);
    }

    public TablePreviewResponse preview(UUID id, String catalog, String schema, String table, int limit) {
        return runtimeService.preview(id, catalog, schema, table, limit);
    }

    public JdbcQueryInspectionResponse inspectQuery(UUID id, String sql) {
        return runtimeService.inspectQuery(id, sql);
    }

    public List<KafkaTopicResponse> listKafkaTopics(UUID id, String keyword, boolean includeInternal) {
        return runtimeService.listKafkaTopics(id, keyword, includeInternal);
    }

    public List<TdEngineTmqTopicResponse> listTdEngineTmqTopics(UUID id, String keyword) {
        return runtimeService.listTdEngineTmqTopics(id, keyword);
    }

    public TdEngineTmqTopicDetailResponse readTdEngineTmqTopic(UUID id, String topicName) {
        return runtimeService.readTdEngineTmqTopic(id, topicName);
    }

    private DataSource requireDataSource(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在"));
    }

    private void validateConfiguration(
            DataSourceType type,
            Set<DataSourcePurpose> purposes,
            DataSourceConnectionRequest connection
    ) {
        if (type.connectionKind() != connection.kind()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据源类型与连接配置不匹配");
        }
        if (!type.supportedPurposes().containsAll(purposes)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, type.displayName() + "不支持所选用途");
        }
        runtimeService.validateConfiguration(type, connection);
    }

    private DataSourceConnection connectionForCreate(
            DataSourceType type,
            DataSourceConnectionRequest request
    ) {
        return connectionFor(type, request, null);
    }

    private DataSourceConnection connectionForUpdate(
            DataSource dataSource,
            DataSourceType type,
            DataSourceConnectionRequest request
    ) {
        DataSourceConnection current = dataSource.getType() == type ? dataSource.getConnection() : null;
        return connectionFor(type, request, current);
    }

    private DataSourceConnection connectionFor(
            DataSourceType type,
            DataSourceConnectionRequest request,
            DataSourceConnection current
    ) {
        return switch (request) {
            case JdbcDataSourceConnectionRequest jdbc -> jdbcConnection(jdbc, current, type);
            case KafkaDataSourceConnectionRequest kafka -> kafkaConnection(kafka, current, type);
            case S3DataSourceConnectionRequest s3 -> s3Connection(s3, current, type);
            case HttpApiDataSourceConnectionRequest httpApi -> httpApiConnection(httpApi, current, type);
        };
    }

    private DataSourceConnection httpApiConnection(
            HttpApiDataSourceConnectionRequest request,
            DataSourceConnection current,
            DataSourceType type
    ) {
        return buildHttpApiConnection(request, current, type, credentialCipher);
    }

    static DataSourceConnection buildHttpApiConnection(
            HttpApiDataSourceConnectionRequest request,
            DataSourceConnection current,
            DataSourceType type,
            DataSourceCredentialCipher credentialCipher
    ) {
        requireKind(type, DataSourceConnectionKind.HTTP_API);
        String baseUrl = normalizeBaseUrl(request.baseUrl()).replaceFirst("/+$", "");
        HttpApiContracts.CredentialBundle previous = current == null
                ? emptyCredentials()
                : HttpApiConfigurationCodec.readCredentials(
                        credentialCipher.decrypt(current.apiCredentialsCiphertextValue()));
        HttpApiContracts.CredentialBundle credentials = mergeCredentials(request, previous);
        HttpApiContracts.AuthenticationConfiguration authentication = authenticationConfiguration(
                request.authentication(), credentials);
        HttpApiContracts.ConnectionConfiguration configuration = new HttpApiContracts.ConnectionConfiguration(
                baseUrl,
                normalizedHeaders(request.defaultHeaders()),
                valueOrDefault(request.connectTimeoutMs(), 5_000),
                valueOrDefault(request.requestTimeoutMs(), 30_000),
                valueOrDefault(request.minimumRequestIntervalMs(), 0),
                valueOrDefault(request.maxRetries(), 2),
                authentication,
                hasText(credentials.signingSecret()),
                hasText(credentials.signingPrivateKey())
        );
        String ciphertext = credentialCipher.encrypt(HttpApiConfigurationCodec.writeCredentials(credentials));
        return DataSourceConnection.httpApi(
                baseUrl,
                HttpApiConfigurationCodec.writeConnectionConfiguration(configuration),
                ciphertext
        );
    }

    private static HttpApiContracts.CredentialBundle mergeCredentials(
            HttpApiDataSourceConnectionRequest request,
            HttpApiContracts.CredentialBundle previous
    ) {
        String password = null;
        String bearerToken = null;
        String apiKey = null;
        String clientSecret = null;
        String tokenEndpointPassword = null;
        switch (request.authentication()) {
            case HttpApiDataSourceConnectionRequest.NoneAuthenticationRequest ignored -> {
            }
            case HttpApiDataSourceConnectionRequest.BasicAuthenticationRequest basic ->
                    password = preserve(basic.password(), previous.password());
            case HttpApiDataSourceConnectionRequest.BearerAuthenticationRequest bearer ->
                    bearerToken = preserve(bearer.token(), previous.bearerToken());
            case HttpApiDataSourceConnectionRequest.ApiKeyAuthenticationRequest key ->
                    apiKey = preserve(key.apiKey(), previous.apiKey());
            case HttpApiDataSourceConnectionRequest.OAuth2AuthenticationRequest oauth ->
                    clientSecret = preserve(oauth.clientSecret(), previous.clientSecret());
            case HttpApiDataSourceConnectionRequest.TokenEndpointAuthenticationRequest token ->
                    tokenEndpointPassword = preserve(token.password(), previous.tokenEndpointPassword());
        }
        return new HttpApiContracts.CredentialBundle(
                password,
                bearerToken,
                apiKey,
                clientSecret,
                tokenEndpointPassword,
                preserve(request.signingSecret(), previous.signingSecret()),
                preserve(request.signingPrivateKey(), previous.signingPrivateKey())
        );
    }

    private static HttpApiContracts.AuthenticationConfiguration authenticationConfiguration(
            HttpApiDataSourceConnectionRequest.AuthenticationRequest request,
            HttpApiContracts.CredentialBundle credentials
    ) {
        return switch (request) {
            case HttpApiDataSourceConnectionRequest.NoneAuthenticationRequest ignored ->
                    new HttpApiContracts.NoneAuthentication();
            case HttpApiDataSourceConnectionRequest.BasicAuthenticationRequest basic -> {
                requireCredential(credentials.password(), "Basic 密码");
                yield new HttpApiContracts.BasicAuthentication(basic.username().trim(), true);
            }
            case HttpApiDataSourceConnectionRequest.BearerAuthenticationRequest ignored -> {
                requireCredential(credentials.bearerToken(), "Bearer Token");
                yield new HttpApiContracts.BearerTokenAuthentication(true);
            }
            case HttpApiDataSourceConnectionRequest.ApiKeyAuthenticationRequest key -> {
                if (key.location() != HttpApiContracts.ValueLocation.HEADER
                        && key.location() != HttpApiContracts.ValueLocation.QUERY) {
                    throw badRequest("API Key 只能放在 Header 或 Query");
                }
                requireCredential(credentials.apiKey(), "API Key");
                String name = placementName(key.name(), key.location(), "API Key");
                String valueTemplate = defaultIfBlank(key.valueTemplate(), "${credential.apiKey}");
                requireTemplateVariable(valueTemplate, "credential.apiKey", "API Key 值模板");
                yield new HttpApiContracts.ApiKeyAuthentication(
                        key.location(), name, valueTemplate, true);
            }
            case HttpApiDataSourceConnectionRequest.OAuth2AuthenticationRequest oauth -> {
                requireCredential(credentials.clientSecret(), "OAuth2 Client Secret");
                HttpApiContracts.ValueLocation location = oauth.tokenLocation() == null
                        ? HttpApiContracts.ValueLocation.HEADER : oauth.tokenLocation();
                if (location == HttpApiContracts.ValueLocation.BODY) {
                    throw badRequest("OAuth2 Token 只能放在 Header 或 Query");
                }
                String tokenName = placementName(
                        defaultIfBlank(oauth.tokenName(), "Authorization"), location, "OAuth2 Token");
                String tokenValueTemplate = defaultIfBlank(oauth.tokenValueTemplate(), "Bearer ${token}");
                requireTemplateVariable(tokenValueTemplate, "token", "OAuth2 Token 值模板");
                yield new HttpApiContracts.OAuth2ClientCredentialsAuthentication(
                        normalizeHttpUrl(oauth.tokenUrl(), "OAuth2 Token URL"),
                        oauth.clientId().trim(),
                        oauth.scopes() == null ? List.of() : oauth.scopes().stream()
                                .filter(DataSourceService::hasText).map(String::trim).distinct().toList(),
                        normalizeOptional(oauth.audience()),
                        location,
                        tokenName,
                        tokenValueTemplate,
                        true
                );
            }
            case HttpApiDataSourceConnectionRequest.TokenEndpointAuthenticationRequest token -> {
                requireCredential(credentials.tokenEndpointPassword(), "Token Endpoint 密码");
                if (!hasText(token.expiresInPointer()) && token.fixedTtlSeconds() == null) {
                    throw badRequest("Token Endpoint 必须配置 expiresInPointer 或固定 TTL");
                }
                HttpApiContracts.ValueLocation location = token.tokenLocation() == null
                        ? HttpApiContracts.ValueLocation.HEADER : token.tokenLocation();
                if (location == HttpApiContracts.ValueLocation.BODY) {
                    throw badRequest("运行时 Token 只能放在 Header 或 Query");
                }
                String tokenName = placementName(
                        defaultIfBlank(token.tokenName(), "Authorization"), location, "运行时 Token");
                String tokenValueTemplate = defaultIfBlank(token.tokenValueTemplate(), "Bearer ${token}");
                requireTemplateVariable(tokenValueTemplate, "token", "运行时 Token 值模板");
                yield new HttpApiContracts.TokenEndpointAuthentication(
                        normalizeHttpUrl(token.tokenUrl(), "Token Endpoint URL"),
                        token.method(),
                        normalizedHeaders(token.headers()),
                        normalizeOptional(token.bodyTemplate()),
                        normalizeOptional(token.username()),
                        token.tokenPointer().trim(),
                        normalizeOptional(token.expiresInPointer()),
                        token.fixedTtlSeconds(),
                        location,
                        tokenName,
                        tokenValueTemplate,
                        true
                );
            }
        };
    }

    private static List<HttpApiContracts.NamedValue> normalizedHeaders(
            List<HttpApiDataSourceConnectionRequest.HeaderRequest> headers
    ) {
        if (headers == null || headers.isEmpty()) {
            return List.of();
        }
        Set<String> names = new java.util.HashSet<>();
        List<HttpApiContracts.NamedValue> result = new java.util.ArrayList<>();
        for (HttpApiDataSourceConnectionRequest.HeaderRequest header : headers) {
            String name = header.name().trim();
            String lower = name.toLowerCase(Locale.ROOT);
            if (!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
                throw badRequest("HTTP Header 名称不合法：" + name);
            }
            if ("authorization".equals(lower) || "proxy-authorization".equals(lower)
                    || "cookie".equals(lower) || lower.contains("token") || lower.contains("signature")
                    || lower.contains("secret") || lower.contains("credential")
                    || lower.contains("api-key") || lower.contains("apikey")
                    || lower.contains("access-key") || lower.contains("accesskey")) {
                throw badRequest("敏感 Header 必须通过鉴权或签名配置维护：" + name);
            }
            if (header.value().indexOf('\r') >= 0 || header.value().indexOf('\n') >= 0) {
                throw badRequest("HTTP Header 值不允许包含换行符：" + name);
            }
            if (!names.add(lower)) {
                throw badRequest("HTTP Header 名称重复：" + name);
            }
            result.add(new HttpApiContracts.NamedValue(name, header.value().trim()));
        }
        return List.copyOf(result);
    }

    private static String normalizeHttpUrl(String value, String label) {
        try {
            URI uri = URI.create(value.trim());
            if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme())
                    || "https".equalsIgnoreCase(uri.getScheme())) || uri.getUserInfo() != null) {
                throw new IllegalArgumentException();
            }
            return uri.toString();
        } catch (RuntimeException exception) {
            throw badRequest(label + "必须是有效的 HTTP 或 HTTPS 地址");
        }
    }

    private static String normalizeBaseUrl(String value) {
        String normalized = normalizeHttpUrl(value, "Base URL");
        URI uri = URI.create(normalized);
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw badRequest("Base URL 不能包含 Query 或 Fragment");
        }
        return normalized;
    }

    private static String placementName(
            String value,
            HttpApiContracts.ValueLocation location,
            String label
    ) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 128 || name.indexOf('\r') >= 0 || name.indexOf('\n') >= 0) {
            throw badRequest(label + "参数名称不合法");
        }
        if (location == HttpApiContracts.ValueLocation.HEADER
                && !name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")) {
            throw badRequest(label + " Header 名称不合法");
        }
        return name;
    }

    private static void requireTemplateVariable(String template, String variable, String label) {
        if (!template.contains("${" + variable + "}")) {
            throw badRequest(label + "必须包含 ${" + variable + "}");
        }
    }

    private static HttpApiContracts.CredentialBundle emptyCredentials() {
        return new HttpApiContracts.CredentialBundle(null, null, null, null, null, null, null);
    }

    private static int valueOrDefault(Integer value, int defaultValue) {
        return value == null ? defaultValue : value;
    }

    private static String preserve(String requested, String current) {
        return requested == null ? current : normalizeOptional(requested);
    }

    private static void requireCredential(String value, String label) {
        if (!hasText(value)) {
            throw badRequest(label + "不能为空");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static DataSourceConnection jdbcConnection(
            JdbcDataSourceConnectionRequest request,
            DataSourceConnection current,
            DataSourceType type
    ) {
        requireKind(type, DataSourceConnectionKind.JDBC);
        Map<String, String> options = request.options() == null && current != null
                ? current.getOptions() : request.options();
        if (ConnectionOptionsConverter.encodedLength(options)
                > ConnectionOptionsConverter.MAX_DATABASE_COLUMN_LENGTH) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "JDBC 连接参数编码后不能超过 4000 个字符"
            );
        }
        String password = request.password() == null && current != null ? current.secretValue() : request.password();
        return DataSourceConnection.jdbc(
                request.host().trim(), request.port(), request.databaseName().trim(), normalizeOptional(request.schemaName()),
                request.username().trim(), password, options
        );
    }

    private static DataSourceConnection kafkaConnection(
            KafkaDataSourceConnectionRequest request,
            DataSourceConnection current,
            DataSourceType type
    ) {
        requireKind(type, DataSourceConnectionKind.KAFKA);
        Map<String, String> options = new LinkedHashMap<>();
        options.put("securityProtocol", defaultIfBlank(request.securityProtocol(), "PLAINTEXT"));
        String mechanism = normalizeOptional(request.saslMechanism());
        if (mechanism != null) {
            options.put("saslMechanism", mechanism);
        }
        String password = request.password() == null && current != null ? current.secretValue() : request.password();
        if (options.get("securityProtocol").startsWith("SASL_")
                && (password == null || password.isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SASL Kafka 密码不能为空");
        }
        return DataSourceConnection.nonJdbc(
                request.bootstrapServers().trim(), null, null, normalizeOptional(request.username()), password, options
        );
    }

    private static DataSourceConnection s3Connection(
            S3DataSourceConnectionRequest request,
            DataSourceConnection current,
            DataSourceType type
    ) {
        requireKind(type, DataSourceConnectionKind.S3);
        String secretKey = request.secretKey() == null && current != null ? current.secretValue() : request.secretKey();
        if (secretKey == null || secretKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "S3 SecretKey 不能为空");
        }
        Map<String, String> options = new LinkedHashMap<>();
        String region = normalizeOptional(request.region());
        if (region != null) {
            options.put("region", region);
        }
        options.put("pathStyleAccess", String.valueOf(request.pathStyleAccess() == null || request.pathStyleAccess()));
        return DataSourceConnection.nonJdbc(
                request.endpoint().trim(), request.bucket().trim(), normalizeRootPrefix(request.rootPrefix()),
                request.accessKey().trim(), secretKey, options
        );
    }

    private static void requireKind(DataSourceType type, DataSourceConnectionKind expectedKind) {
        if (type.connectionKind() != expectedKind) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据源类型与连接配置不匹配");
        }
    }

    private static String normalizeCode(String code) {
        return code.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeOptional(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static String normalizeRootPrefix(String value) {
        String normalized = normalizeOptional(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replaceFirst("^/+", "");
        normalized = normalized.replaceFirst("/+$", "");
        return normalized.isEmpty() ? null : normalized;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        String normalized = normalizeOptional(value);
        return normalized == null ? defaultValue : normalized;
    }
}

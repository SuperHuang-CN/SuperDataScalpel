package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnection;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceConnectionKind;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.web.request.CreateDataSourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.DataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.JdbcDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.KafkaDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.S3DataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.TestDataSourceConnectionRequest;
import cn.superhuang.data.scalpel.business.datasource.web.request.UpdateDataSourceRequest;
import cn.superhuang.data.scalpel.business.datasource.web.response.ConnectionTestResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.DataSourceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.NamespaceResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableListResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TableMetadataResponse;
import cn.superhuang.data.scalpel.business.datasource.web.response.TablePreviewResponse;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.service.ServiceEngineDataSourceRegistrationService;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class DataSourceService {

    private final DataSourceRepository repository;
    private final SearchEngine searchEngine;
    private final DirectoryService directoryService;
    private final DataSourceRuntimeService runtimeService;
    private final DataModelRepository dataModelRepository;
    private final ServiceEngineDataSourceRegistrationService engineDataSourceRegistrationService;

    public DataSourceService(
            DataSourceRepository repository,
            SearchEngine searchEngine,
            DirectoryService directoryService,
            DataSourceRuntimeService runtimeService,
            DataModelRepository dataModelRepository,
            ServiceEngineDataSourceRegistrationService engineDataSourceRegistrationService
    ) {
        this.repository = repository;
        this.searchEngine = searchEngine;
        this.directoryService = directoryService;
        this.runtimeService = runtimeService;
        this.dataModelRepository = dataModelRepository;
        this.engineDataSourceRegistrationService = engineDataSourceRegistrationService;
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
        if (dataModelRepository.existsByStorageDataSourceId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源已被模型使用，不能删除");
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

    private static DataSourceConnection connectionForCreate(
            DataSourceType type,
            DataSourceConnectionRequest request
    ) {
        return connectionFor(type, request, null);
    }

    private static DataSourceConnection connectionForUpdate(
            DataSource dataSource,
            DataSourceType type,
            DataSourceConnectionRequest request
    ) {
        DataSourceConnection current = dataSource.getType() == type ? dataSource.getConnection() : null;
        return connectionFor(type, request, current);
    }

    private static DataSourceConnection connectionFor(
            DataSourceType type,
            DataSourceConnectionRequest request,
            DataSourceConnection current
    ) {
        return switch (request) {
            case JdbcDataSourceConnectionRequest jdbc -> jdbcConnection(jdbc, current, type);
            case KafkaDataSourceConnectionRequest kafka -> kafkaConnection(kafka, current, type);
            case S3DataSourceConnectionRequest s3 -> s3Connection(s3, current, type);
        };
    }

    private static DataSourceConnection jdbcConnection(
            JdbcDataSourceConnectionRequest request,
            DataSourceConnection current,
            DataSourceType type
    ) {
        requireKind(type, DataSourceConnectionKind.JDBC);
        Map<String, String> options = request.options() == null && current != null
                ? current.getOptions() : request.options();
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

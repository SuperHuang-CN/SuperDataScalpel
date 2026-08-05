package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineDataSourceRegistration;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineDataSourceRegistrationStatus;
import cn.superhuang.data.scalpel.business.service.domain.ScriptDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.domain.SqlDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.domain.StandardDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineDataSourceRegistrationRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.business.service.repository.ScriptDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.repository.SqlDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.repository.StandardDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.web.request.CreateServiceEngineDataSourceRegistrationRequest;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineDataSourceRegistrationResponse;
import cn.superhuang.data.scalpel.business.service.web.response.ServiceEngineDataSourceTestResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRegistrationResponse;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceRemovalRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceStatus;
import cn.superhuang.data.scalpel.contract.service.EngineDataSourceTestResponse;
import cn.superhuang.data.scalpel.contract.service.JdbcDataSourceSnapshot;
import cn.superhuang.data.scalpel.contract.service.ServiceEngineInfoResponse;
import cn.superhuang.data.scalpel.dialect.api.DatabaseCapability;
import cn.superhuang.data.scalpel.dialect.api.DialectRegistry;
import cn.superhuang.data.scalpel.search.SearchEngine;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;

/** Coordinates explicitly registered JDBC data sources with remote Service Engines. */
@Service
public class ServiceEngineDataSourceRegistrationService {

    private final ServiceEngineDataSourceRegistrationRepository repository;
    private final ServiceEngineRepository engineRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DataModelRepository modelRepository;
    private final DataServiceRepository dataServiceRepository;
    private final StandardDataServiceDefinitionRepository standardDefinitionRepository;
    private final SqlDataServiceDefinitionRepository sqlDefinitionRepository;
    private final ScriptDataServiceDefinitionRepository scriptDefinitionRepository;
    private final DialectRegistry dialectRegistry;
    private final ServiceEngineClient engineClient;
    private final SearchEngine searchEngine;
    private final TransactionTemplate transactionTemplate;

    public ServiceEngineDataSourceRegistrationService(
            ServiceEngineDataSourceRegistrationRepository repository,
            ServiceEngineRepository engineRepository,
            DataSourceRepository dataSourceRepository,
            DataModelRepository modelRepository,
            DataServiceRepository dataServiceRepository,
            StandardDataServiceDefinitionRepository standardDefinitionRepository,
            SqlDataServiceDefinitionRepository sqlDefinitionRepository,
            ScriptDataServiceDefinitionRepository scriptDefinitionRepository,
            DialectRegistry dialectRegistry,
            ServiceEngineClient engineClient,
            SearchEngine searchEngine,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.engineRepository = engineRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.modelRepository = modelRepository;
        this.dataServiceRepository = dataServiceRepository;
        this.standardDefinitionRepository = standardDefinitionRepository;
        this.sqlDefinitionRepository = sqlDefinitionRepository;
        this.scriptDefinitionRepository = scriptDefinitionRepository;
        this.dialectRegistry = dialectRegistry;
        this.engineClient = engineClient;
        this.searchEngine = searchEngine;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PageResponse<ServiceEngineDataSourceRegistrationResponse> search(SearchRequest request) {
        Page<ServiceEngineDataSourceRegistration> result = searchEngine.search(
                request, ServiceEngineDataSourceRegistration.class, repository
        );
        return new PageResponse<>(
                result.getContent().stream().map(this::response).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public ServiceEngineDataSourceRegistrationResponse get(UUID id) {
        return response(requireRegistration(id));
    }

    public ServiceEngineDataSourceRegistrationResponse create(CreateServiceEngineDataSourceRegistrationRequest request) {
        UUID registrationId = requireTransactionResult(transactionTemplate.execute(status -> {
            ServiceEngine engine = requireEnabledEngine(request.engineId());
            requireRuntimeDataSource(request.dataSourceId());
            if (repository.findByEngineIdAndDataSourceId(engine.getId(), request.dataSourceId()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "该数据源已注册到服务引擎");
            }
            return repository.saveAndFlush(
                    ServiceEngineDataSourceRegistration.pending(engine.getId(), request.dataSourceId())
            ).getId();
        }));
        return synchronize(registrationId);
    }

    public ServiceEngineDataSourceRegistrationResponse sync(UUID id) {
        return synchronize(id);
    }

    public ServiceEngineDataSourceTestResponse test(UUID id) {
        TestPreparation preparation = requireTransactionResult(
                transactionTemplate.execute(status -> prepareTest(id))
        );
        try {
            EngineDataSourceTestResponse response = engineClient.testDataSource(
                    preparation.engine(), preparation.dataSourceId()
            );
            if (response == null || !preparation.engine().matchesCode(response.engineCode())
                    || !preparation.dataSourceId().equals(response.dataSourceId())) {
                throw new IllegalStateException("服务引擎未确认数据源测试结果");
            }
            return new ServiceEngineDataSourceTestResponse(
                    preparation.registrationId(), response.engineCode(), response.dataSourceId(),
                    response.databaseType()
            );
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Engine 数据源测试失败：" + safeMessage(exception), exception);
        }
    }

    private TestPreparation prepareTest(UUID id) {
        ServiceEngineDataSourceRegistration registration = requireRegistration(id);
        if (registration.getStatus() != ServiceEngineDataSourceRegistrationStatus.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已就绪的数据源可以测试，请先同步");
        }
        return new TestPreparation(
                registration.getId(), requireEngine(registration.getEngineId()), registration.getDataSourceId()
        );
    }

    public void delete(UUID id) {
        DeletePreparation preparation = requireTransactionResult(
                transactionTemplate.execute(status -> prepareDelete(id))
        );
        try {
            EngineDataSourceRegistrationResponse response = engineClient.removeDataSource(
                    preparation.engine(),
                    new EngineDataSourceRemovalRequest(preparation.dataSourceId())
            );
            if (response == null || response.status() != EngineDataSourceStatus.REMOVED
                    || !preparation.dataSourceId().equals(response.dataSourceId())) {
                throw new IllegalStateException("服务引擎未确认数据源移除");
            }
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Engine 数据源移除失败：" + safeMessage(exception), exception);
        }
        transactionTemplate.executeWithoutResult(status -> completeDelete(preparation));
    }

    private DeletePreparation prepareDelete(UUID id) {
        ServiceEngineDataSourceRegistration registration = requireRegistration(id);
        assertNoEnabledService(registration.getEngineId(), registration.getDataSourceId());
        return new DeletePreparation(
                registration.getId(), requireEngine(registration.getEngineId()),
                registration.getDataSourceId()
        );
    }

    private void completeDelete(DeletePreparation preparation) {
        ServiceEngineDataSourceRegistration registration = requireRegistration(preparation.registrationId());
        assertNoEnabledService(registration.getEngineId(), registration.getDataSourceId());
        repository.delete(registration);
    }

    private ServiceEngineDataSourceRegistrationResponse synchronize(UUID registrationId) {
        SyncPreparation preparation = requireTransactionResult(
                transactionTemplate.execute(status -> prepareSynchronization(registrationId))
        );
        String failure = null;
        String snapshotDigest = null;
        try {
            verifyEngineCapability(preparation.engine(), preparation.dataSource());
            EngineDataSourceRegistrationResponse response = engineClient.registerDataSource(
                    preparation.engine(),
                    new EngineDataSourceRegistrationRequest(
                            preparation.dataSourceId(), preparation.snapshot()
                    )
            );
            if (response == null || !preparation.engine().matchesCode(response.engineCode())
                    || !preparation.dataSourceId().equals(response.dataSourceId())
                    || response.status() != EngineDataSourceStatus.READY) {
                throw new IllegalStateException("服务引擎未确认数据源同步结果");
            }
            snapshotDigest = digest(preparation.snapshot());
        } catch (RuntimeException exception) {
            failure = safeMessage(exception);
        }
        String finalFailure = failure;
        String finalSnapshotDigest = snapshotDigest;
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeSynchronization(preparation, finalSnapshotDigest, finalFailure)
        ));
    }

    private SyncPreparation prepareSynchronization(UUID registrationId) {
        ServiceEngineDataSourceRegistration registration = requireRegistration(registrationId);
        registration.beginSync();
        repository.saveAndFlush(registration);
        ServiceEngine engine = requireEnabledEngine(registration.getEngineId());
        DataSource dataSource = requireRuntimeDataSource(registration.getDataSourceId());
        JdbcDataSourceSnapshot snapshot = snapshot(dataSource);
        return new SyncPreparation(
                registration.getId(), engine, dataSource, registration.getDataSourceId(), snapshot
        );
    }

    private ServiceEngineDataSourceRegistrationResponse completeSynchronization(
            SyncPreparation preparation,
            String snapshotDigest,
            String failure
    ) {
        ServiceEngineDataSourceRegistration registration = requireRegistration(preparation.registrationId());
        if (failure == null) {
            registration.ready(snapshotDigest);
        } else {
            registration.failed(failure);
        }
        return response(repository.saveAndFlush(registration));
    }

    /** Ensures only an Engine-local, synchronized data source can be used for a publish. */
    @Transactional(readOnly = true)
    public void requireReadyRegistration(UUID engineId, UUID dataSourceId) {
        ServiceEngineDataSourceRegistration registration = repository.findByEngineIdAndDataSourceId(engineId, dataSourceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "模型数据源尚未注册到所选服务引擎"));
        if (registration.getStatus() != ServiceEngineDataSourceRegistrationStatus.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型数据源尚未同步就绪，请先在服务引擎中同步");
        }
    }

    /** Draft services still need an explicit Engine/data source relationship, even if it is outdated. */
    @Transactional(readOnly = true)
    public void requireRegistration(UUID engineId, UUID dataSourceId) {
        if (repository.findByEngineIdAndDataSourceId(engineId, dataSourceId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型数据源尚未注册到所选服务引擎");
        }
    }

    /** Called before a data source changes a runtime capability used by an enabled service. */
    @Transactional(readOnly = true)
    public void assertCanChangeRuntimeCapability(
            DataSource dataSource,
            DataSourceType nextType,
            Collection<DataSourcePurpose> nextPurposes,
            boolean nextEnabled
    ) {
        boolean nextJdbcEnabled = nextEnabled && nextType.isJdbc();
        if (!nextJdbcEnabled && hasEnabledServiceForDataSource(dataSource.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有已启用服务使用该数据源，不能停用或移除 JDBC 能力");
        }
        if (!nextPurposes.contains(DataSourcePurpose.STORAGE)
                && hasEnabledStandardServiceForDataSource(dataSource.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有已启用标准服务使用该数据源，不能移除存储用途");
        }
        if (hasEnabledSqlServiceForDataSource(dataSource.getId())
                && !dialectRegistry.require(nextType.name()).definition().capabilities()
                .contains(DatabaseCapability.SQL_SERVICE_QUERY)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已有已启用 SQL 服务使用该数据源，不能切换到不支持的数据库类型");
        }
    }

    /** Marks every Engine snapshot stale after a connection or runtime capability change. */
    @Transactional
    public void markOutdatedIfRuntimeSignatureChanged(UUID dataSourceId, String previousSignature, DataSource current) {
        if (previousSignature.equals(runtimeSignature(current))) {
            return;
        }
        repository.findAllByDataSourceId(dataSourceId).forEach(ServiceEngineDataSourceRegistration::markOutdated);
    }

    @Transactional(readOnly = true)
    public void assertDataSourceDeletable(UUID dataSourceId) {
        if (repository.existsByDataSourceId(dataSourceId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源已注册到服务引擎，请先解除注册");
        }
    }

    @Transactional(readOnly = true)
    public void assertEngineDeletable(UUID engineId) {
        if (repository.existsByEngineId(engineId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎仍有已注册数据源，不能删除");
        }
    }

    public String runtimeSignature(DataSource dataSource) {
        return digest(dataSource.getType().name() + "|" + dataSource.isEnabled() + "|" + dataSource.getPurposes() + "|"
                + dataSource.getConnection().getHost() + "|" + dataSource.getConnection().getPort() + "|"
                + dataSource.getConnection().getDatabaseName() + "|" + dataSource.getConnection().getSchemaName() + "|"
                + dataSource.getConnection().getUsername() + "|" + dataSource.getConnection().secretValue() + "|"
                + stableOptions(dataSource.getConnection().getOptions()));
    }

    private static String stableOptions(Map<String, String> options) {
        return options.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> entry.getKey().length() + ":" + entry.getKey()
                        + entry.getValue().length() + ":" + entry.getValue())
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private void verifyEngineCapability(ServiceEngine engine, DataSource dataSource) {
        ServiceEngineInfoResponse info = engineClient.info(engine);
        if (info == null || !engine.matchesCode(info.code())) {
            throw new IllegalStateException("服务引擎身份校验失败");
        }
        if (!info.databaseTypes().contains(dataSource.getType().name())) {
            throw new IllegalStateException("服务引擎不支持 " + dataSource.getType().displayName() + " 数据库");
        }
    }

    private void assertNoEnabledService(UUID engineId, UUID dataSourceId) {
        List<UUID> serviceIds = serviceIdsForDataSource(dataSourceId);
        if (!serviceIds.isEmpty() && dataServiceRepository.existsByEngineIdAndIdInAndStatus(
                engineId, serviceIds, DataServiceStatus.ENABLED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仍有已启用服务使用该数据源，不能解除注册");
        }
    }

    private boolean hasEnabledServiceForDataSource(UUID dataSourceId) {
        List<UUID> serviceIds = serviceIdsForDataSource(dataSourceId);
        return !serviceIds.isEmpty() && dataServiceRepository.existsByIdInAndStatus(serviceIds, DataServiceStatus.ENABLED);
    }

    private boolean hasEnabledStandardServiceForDataSource(UUID dataSourceId) {
        List<UUID> serviceIds = standardServiceIdsForDataSource(dataSourceId);
        return !serviceIds.isEmpty() && dataServiceRepository.existsByIdInAndStatus(serviceIds, DataServiceStatus.ENABLED);
    }

    private boolean hasEnabledSqlServiceForDataSource(UUID dataSourceId) {
        List<UUID> serviceIds = sqlServiceIdsForDataSource(dataSourceId);
        return !serviceIds.isEmpty() && dataServiceRepository.existsByIdInAndStatus(serviceIds, DataServiceStatus.ENABLED);
    }

    private List<UUID> serviceIdsForDataSource(UUID dataSourceId) {
        LinkedHashSet<UUID> serviceIds = new LinkedHashSet<>(standardServiceIdsForDataSource(dataSourceId));
        serviceIds.addAll(sqlServiceIdsForDataSource(dataSourceId));
        serviceIds.addAll(scriptServiceIdsForDataSource(dataSourceId));
        return List.copyOf(serviceIds);
    }

    private List<UUID> standardServiceIdsForDataSource(UUID dataSourceId) {
        List<UUID> modelIds = modelRepository.findAllByStorageDataSourceId(dataSourceId).stream()
                .map(DataModel::getId).toList();
        LinkedHashSet<UUID> serviceIds = new LinkedHashSet<>();
        if (!modelIds.isEmpty()) {
            standardDefinitionRepository.findAllByModelIdIn(modelIds).stream()
                    .map(StandardDataServiceDefinition::getDataServiceId)
                    .forEach(serviceIds::add);
        }
        return List.copyOf(serviceIds);
    }

    private List<UUID> sqlServiceIdsForDataSource(UUID dataSourceId) {
        return sqlDefinitionRepository.findAllByDataSourceId(dataSourceId).stream()
                .map(SqlDataServiceDefinition::getDataServiceId)
                .toList();
    }

    private List<UUID> scriptServiceIdsForDataSource(UUID dataSourceId) {
        return scriptDefinitionRepository.findAllByDataSourceId(dataSourceId).stream()
                .map(ScriptDataServiceDefinition::getDataServiceId)
                .toList();
    }

    private ServiceEngineDataSourceRegistrationResponse response(ServiceEngineDataSourceRegistration registration) {
        ServiceEngine engine = engineRepository.findById(registration.getEngineId()).orElse(null);
        DataSource dataSource = dataSourceRepository.findById(registration.getDataSourceId()).orElse(null);
        return ServiceEngineDataSourceRegistrationResponse.from(
                registration,
                engine == null ? "已删除" : engine.getCode(),
                engine == null ? "已删除" : engine.getName(),
                dataSource == null ? "已删除" : dataSource.getCode(),
                dataSource == null ? "已删除" : dataSource.getName(),
                dataSource == null ? null : dataSource.getType().name()
        );
    }

    private ServiceEngineDataSourceRegistration requireRegistration(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "服务引擎数据源注册不存在"));
    }

    private ServiceEngine requireEngine(UUID id) {
        return engineRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎不存在"));
    }

    private ServiceEngine requireEnabledEngine(UUID id) {
        ServiceEngine engine = requireEngine(id);
        if (!engine.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎已停用");
        }
        return engine;
    }

    private DataSource requireRuntimeDataSource(UUID id) {
        DataSource dataSource = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "数据源不存在"));
        if (!isRuntimeEligible(dataSource)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只能注册已启用的 JDBC 数据源");
        }
        if (dataSource.getConnection().getPort() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "JDBC 数据源缺少端口");
        }
        return dataSource;
    }

    private static boolean isRuntimeEligible(DataSource dataSource) {
        return dataSource.isEnabled() && dataSource.getType().isJdbc();
    }

    private static JdbcDataSourceSnapshot snapshot(DataSource dataSource) {
        return new JdbcDataSourceSnapshot(
                dataSource.getId(), dataSource.getType().name(), dataSource.getConnection().getHost(),
                dataSource.getConnection().getPort(), dataSource.getConnection().getDatabaseName(),
                dataSource.getConnection().getSchemaName(), dataSource.getConnection().getUsername(),
                dataSource.getConnection().secretValue(), dataSource.getConnection().getOptions()
        );
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "服务引擎调用失败" : message.substring(0, Math.min(500, message.length()));
    }

    private static String digest(Object value) {
        try {
            byte[] bytes = String.valueOf(value).getBytes(StandardCharsets.UTF_8);
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", exception);
        }
    }

    private static <T> T requireTransactionResult(T value) {
        if (value == null) {
            throw new IllegalStateException("事务未返回服务引擎数据源处理结果");
        }
        return value;
    }

    private record SyncPreparation(
            UUID registrationId,
            ServiceEngine engine,
            DataSource dataSource,
            UUID dataSourceId,
            JdbcDataSourceSnapshot snapshot
    ) {
    }

    private record TestPreparation(UUID registrationId, ServiceEngine engine, UUID dataSourceId) {
    }

    private record DeletePreparation(
            UUID registrationId,
            ServiceEngine engine,
            UUID dataSourceId
    ) {
    }
}

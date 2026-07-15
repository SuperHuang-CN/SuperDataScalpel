package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.directory.domain.DirectoryScope;
import cn.superhuang.data.scalpel.business.directory.service.DirectoryService;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTableInspection;
import cn.superhuang.data.scalpel.business.model.service.ModelPhysicalTablePort;
import cn.superhuang.data.scalpel.business.model.service.PhysicalTableState;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeployment;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeploymentStatus;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.domain.ServiceRoutePath;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceDeploymentRepository;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.business.service.web.request.CreateDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.service.EngineDeploymentStatus;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceFieldDefinition;
import cn.superhuang.data.scalpel.contract.service.ServiceUndeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.StandardServiceDefinition;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Control-plane lifecycle for the fixed V1, one-model standard data service. */
@Service
public class DataServiceManagementService {

    private final DataServiceRepository repository;
    private final DataServiceDeploymentRepository deploymentRepository;
    private final ServiceEngineRepository engineRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DirectoryService directoryService;
    private final ModelPhysicalTablePort physicalTablePort;
    private final ServiceEngineClient engineClient;
    private final ServiceEngineDataSourceRegistrationService dataSourceRegistrationService;
    private final SearchEngine searchEngine;
    private final TransactionTemplate transactionTemplate;

    public DataServiceManagementService(
            DataServiceRepository repository,
            DataServiceDeploymentRepository deploymentRepository,
            ServiceEngineRepository engineRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            DirectoryService directoryService,
            ModelPhysicalTablePort physicalTablePort,
            ServiceEngineClient engineClient,
            ServiceEngineDataSourceRegistrationService dataSourceRegistrationService,
            SearchEngine searchEngine,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.deploymentRepository = deploymentRepository;
        this.engineRepository = engineRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.directoryService = directoryService;
        this.physicalTablePort = physicalTablePort;
        this.engineClient = engineClient;
        this.dataSourceRegistrationService = dataSourceRegistrationService;
        this.searchEngine = searchEngine;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional(readOnly = true)
    public PageResponse<DataServiceResponse> search(SearchRequest request) {
        Page<DataService> result = searchEngine.search(request, DataService.class, repository);
        Map<UUID, DataServiceDeployment> deployments = deploymentsByServiceId(result.getContent());
        return new PageResponse<>(
                result.getContent().stream().map(service -> DataServiceResponse.from(service, deployments.get(service.getId()))).toList(),
                result.getTotalElements(), result.getTotalPages(), result.getNumber(), result.getSize()
        );
    }

    @Transactional(readOnly = true)
    public DataServiceResponse get(UUID id) {
        DataService service = requireService(id);
        return DataServiceResponse.from(service, deploymentRepository.findByDataServiceId(id).orElse(null));
    }

    @Transactional
    public DataServiceResponse create(CreateDataServiceRequest request) {
        String code = request.code().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务编码已存在");
        }
        directoryService.validateAssignment(DirectoryScope.DATA_SERVICE, request.directoryId());
        DataModel model = requireModel(request.modelId());
        requireEngine(request.engineId());
        dataSourceRegistrationService.requireRegistration(request.engineId(), model.getStorageDataSourceId());
        String routePath = normalizeRoutePath(request.routePath());
        requireRouteAvailable(request.engineId(), routePath, null);
        DataService service = DataService.create(
                code, request.name(), request.directoryId(), request.modelId(), request.engineId(), routePath, request.description()
        );
        return DataServiceResponse.from(repository.saveAndFlush(service), null);
    }

    @Transactional
    public DataServiceResponse update(UUID id, UpdateDataServiceRequest request) {
        DataService service = requireService(id);
        requireModifiable(service);
        directoryService.validateAssignment(DirectoryScope.DATA_SERVICE, request.directoryId());
        DataModel model = requireModel(request.modelId());
        requireEngine(request.engineId());
        dataSourceRegistrationService.requireRegistration(request.engineId(), model.getStorageDataSourceId());
        String routePath = normalizeRoutePath(request.routePath());
        requireRouteAvailable(request.engineId(), routePath, id);
        service.update(request.name(), request.directoryId(), request.modelId(), request.engineId(), routePath, request.description());
        return DataServiceResponse.from(repository.saveAndFlush(service), deploymentRepository.findByDataServiceId(id).orElse(null));
    }

    public DataServiceResponse publish(UUID id) {
        PublishPreparation preparation = requireTransactionResult(
                transactionTemplate.execute(status -> preparePublish(id))
        );
        ModelPhysicalTableInspection inspection = physicalTablePort.inspect(
                preparation.dataSource(), preparation.model(), preparation.fields()
        );
        if (inspection.state() != PhysicalTableState.MATCHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型物理表未就绪：" + inspection.message());
        }
        PublishCommand command = requireTransactionResult(
                transactionTemplate.execute(status -> beginPublish(preparation))
        );

        String failure = null;
        try {
            ServiceDeploymentResponse response = engineClient.deploy(command.engine(), command.request());
            if (response == null || response.status() != EngineDeploymentStatus.DEPLOYED
                    || response.revision() != command.revision()) {
                throw new IllegalStateException("服务引擎未确认当前版本部署");
            }
        } catch (RuntimeException exception) {
            failure = safeMessage(exception);
        }
        String finalFailure = failure;
        return requireTransactionResult(transactionTemplate.execute(
                status -> completePublish(id, command.revision(), finalFailure)
        ));
    }

    private PublishPreparation preparePublish(UUID id) {
        DataService service = requireService(id);
        if (service.getStatus() == DataServiceStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布服务不能重复发布，请先下线");
        }
        ServiceEngine engine = requireEnabledEngine(service.getEngineId());
        DataModel model = requireModel(service.getModelId());
        if (model.getStatus() != DataModelStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只能发布绑定已发布模型的数据服务");
        }
        DataSource dataSource = requireRuntimeDataSource(model.getStorageDataSourceId());
        dataSourceRegistrationService.requireReadyRegistration(service.getEngineId(), dataSource.getId());
        List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId());
        if (fields.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型未定义字段");
        }
        if (dataSource.getConnection().getPort() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "JDBC 数据源缺少端口");
        }
        StandardServiceDefinition definition = new StandardServiceDefinition(
                1,
                model.getCatalogName(),
                model.getSchemaName(),
                model.getPhysicalTableName(),
                fields.stream().map(field -> new ServiceFieldDefinition(
                        field.getCode(), field.getCode(), field.getFieldType(),
                        field.isNullable(), field.isPrimaryKey()
                )).toList()
        );
        return new PublishPreparation(service, engine, model, dataSource, fields, new PublishedSnapshot(service, definition, dataSource.getId()));
    }

    private PublishCommand beginPublish(PublishPreparation preparation) {
        DataService service = requireService(preparation.service().getId());
        if (service.getStatus() == DataServiceStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布服务不能重复发布，请先下线");
        }
        if (!Objects.equals(service.getUpdatedAt(), preparation.service().getUpdatedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务定义已发生变化，请重新发布");
        }
        long revision = service.nextRevision();
        DataServiceDeployment deployment = deploymentRepository.findByDataServiceId(service.getId())
                .orElseGet(() -> DataServiceDeployment.pending(service.getId(), revision));
        deployment.begin(revision);
        repository.saveAndFlush(service);
        deploymentRepository.saveAndFlush(deployment);
        return new PublishCommand(preparation.engine(), preparation.snapshot().toRequest(revision), revision);
    }

    private DataServiceResponse completePublish(UUID id, long revision, String failure) {
        DataService service = requireService(id);
        DataServiceDeployment deployment = deploymentRepository.findByDataServiceId(id)
                .orElseThrow(() -> new IllegalStateException("数据服务缺少发布状态"));
        if (service.getRevision() != revision || deployment.getRevision() != revision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务发布版本已发生变化");
        }
        if (failure == null) {
            deployment.deployed();
            service.markPublished();
        } else {
            deployment.failed(failure);
        }
        deploymentRepository.saveAndFlush(deployment);
        return DataServiceResponse.from(repository.saveAndFlush(service), deployment);
    }

    public DataServiceResponse disable(UUID id) {
        DisableCommand command = requireTransactionResult(
                transactionTemplate.execute(status -> beginDisable(id))
        );
        String failure = null;
        try {
            ServiceDeploymentResponse response = engineClient.remove(
                    command.engine(), new ServiceUndeploymentRequest(id, command.revision())
            );
            if (response == null || response.status() != EngineDeploymentStatus.REMOVED
                    || response.revision() != command.revision()) {
                throw new IllegalStateException("服务引擎未确认当前版本下线");
            }
        } catch (RuntimeException exception) {
            failure = safeMessage(exception);
        }
        String finalFailure = failure;
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeDisable(id, command.revision(), finalFailure)
        ));
    }

    private DisableCommand beginDisable(UUID id) {
        DataService service = requireService(id);
        if (service.getStatus() != DataServiceStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只有已发布服务可以下线");
        }
        ServiceEngine engine = requireEngine(service.getEngineId());
        DataServiceDeployment deployment = deploymentRepository.findByDataServiceId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "服务缺少部署状态，不能下线"));
        deployment.beginRemoval();
        deploymentRepository.saveAndFlush(deployment);
        return new DisableCommand(engine, service.getRevision());
    }

    private DataServiceResponse completeDisable(UUID id, long revision, String failure) {
        DataService service = requireService(id);
        DataServiceDeployment deployment = deploymentRepository.findByDataServiceId(id)
                .orElseThrow(() -> new IllegalStateException("数据服务缺少部署状态"));
        if (service.getRevision() != revision || deployment.getRevision() != revision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务下线版本已发生变化");
        }
        if (failure == null) {
            deployment.removed();
            service.markDisabled();
        } else {
            deployment.failed(failure);
        }
        deploymentRepository.saveAndFlush(deployment);
        return DataServiceResponse.from(repository.saveAndFlush(service), deployment);
    }

    @Transactional
    public void delete(UUID id) {
        DataService service = requireService(id);
        if (service.getStatus() == DataServiceStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布服务请先下线后再删除");
        }
        DataServiceDeployment deployment = deploymentRepository.findByDataServiceId(id).orElse(null);
        if (deployment != null && deployment.getStatus() != DataServiceDeploymentStatus.REMOVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务部署状态未确认移除，不能删除");
        }
        if (deployment != null) {
            deploymentRepository.delete(deployment);
        }
        repository.delete(service);
    }

    private DataSource requireRuntimeDataSource(UUID id) {
        DataSource dataSource = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "模型的数据源不存在"));
        if (!dataSource.isEnabled() || !dataSource.getPurposes().contains(DataSourcePurpose.STORAGE) || !dataSource.getType().isJdbc()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型必须绑定已启用的 JDBC 数据存储");
        }
        return dataSource;
    }

    private void requireModifiable(DataService service) {
        if (service.getStatus() == DataServiceStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已发布服务请先下线后再修改");
        }
        DataServiceDeployment deployment = deploymentRepository.findByDataServiceId(service.getId()).orElse(null);
        if (deployment != null && deployment.getStatus() != DataServiceDeploymentStatus.REMOVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务部署状态未确认移除，不能修改");
        }
    }

    private void requireRouteAvailable(UUID engineId, String routePath, UUID currentId) {
        boolean exists = currentId == null
                ? repository.existsByEngineIdAndRoutePath(engineId, routePath)
                : repository.existsByEngineIdAndRoutePathAndIdNot(engineId, routePath, currentId);
        if (exists) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该服务引擎上的请求路径已被占用");
        }
    }

    private String normalizeRoutePath(String routePath) {
        try {
            return ServiceRoutePath.normalize(routePath);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private DataService requireService(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在"));
    }

    private DataModel requireModel(UUID id) {
        return modelRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "模型不存在"));
    }

    private ServiceEngine requireEngine(UUID id) {
        return engineRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "服务引擎不存在"));
    }

    private ServiceEngine requireEnabledEngine(UUID id) {
        ServiceEngine engine = requireEngine(id);
        if (!engine.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎已停用");
        }
        return engine;
    }

    private Map<UUID, DataServiceDeployment> deploymentsByServiceId(List<DataService> services) {
        if (services.isEmpty()) {
            return Map.of();
        }
        Map<UUID, DataServiceDeployment> result = new HashMap<>();
        deploymentRepository.findAllByDataServiceIdIn(services.stream().map(DataService::getId).toList())
                .forEach(deployment -> result.put(deployment.getDataServiceId(), deployment));
        return result;
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "服务引擎调用失败" : message.substring(0, Math.min(500, message.length()));
    }

    private static String digest(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte item : hash) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static <T> T requireTransactionResult(T value) {
        if (value == null) {
            throw new IllegalStateException("事务未返回数据服务处理结果");
        }
        return value;
    }

    private record PublishPreparation(
            DataService service,
            ServiceEngine engine,
            DataModel model,
            DataSource dataSource,
            List<DataModelField> fields,
            PublishedSnapshot snapshot
    ) {
    }

    private record PublishCommand(ServiceEngine engine, ServiceDeploymentRequest request, long revision) {
    }

    private record DisableCommand(ServiceEngine engine, long revision) {
    }

    private record PublishedSnapshot(
            DataService service,
            StandardServiceDefinition definition,
            UUID dataSourceId
    ) {
        private ServiceDeploymentRequest toRequest(long revision) {
            List<ServiceFieldDefinition> sortedFields = definition.fields().stream()
                    .sorted(Comparator.comparing(ServiceFieldDefinition::code)).toList();
            String fingerprint = service.getId() + "|" + revision + "|" + service.getRoutePath() + "|"
                    + dataSourceId + "|"
                    + definition.catalogName() + "|" + definition.schemaName() + "|" + definition.physicalTableName() + "|"
                    + sortedFields;
            return new ServiceDeploymentRequest(
                    service.getId(), revision, service.getCode(), service.getRoutePath(), digest(fingerprint), definition, dataSourceId
            );
        }
    }
}

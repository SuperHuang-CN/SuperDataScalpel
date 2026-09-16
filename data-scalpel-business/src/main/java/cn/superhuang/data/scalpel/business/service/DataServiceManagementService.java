package cn.superhuang.data.scalpel.business.service;

import cn.superhuang.data.scalpel.business.cartography.model.SpatialStyleDocument;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourcePurpose;
import cn.superhuang.data.scalpel.business.datasource.domain.DataSourceType;
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
import cn.superhuang.data.scalpel.business.service.domain.DataServiceAccessMode;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeployment;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceDeploymentStatus;
import cn.superhuang.data.scalpel.business.service.domain.DataServiceStatus;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngine;
import cn.superhuang.data.scalpel.business.service.domain.ServiceEngineType;
import cn.superhuang.data.scalpel.business.service.domain.ServiceRoutePath;
import cn.superhuang.data.scalpel.business.service.domain.ScriptDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.domain.SqlDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.domain.SqlDataServiceModelReference;
import cn.superhuang.data.scalpel.business.service.domain.SqlDataServiceParameter;
import cn.superhuang.data.scalpel.business.service.domain.StandardDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.domain.SpatialDataServiceDefinition;
import cn.superhuang.data.scalpel.business.service.domain.SpatialGeometryFamily;
import cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServiceBinding;
import cn.superhuang.data.scalpel.business.service.gateway.repository.GatewayServiceBindingRepository;
import cn.superhuang.data.scalpel.business.service.consumer.subscription.repository.ApiServiceSubscriptionRepository;
import cn.superhuang.data.scalpel.business.service.gateway.service.DataServiceGatewayPublicationService;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceDeploymentRepository;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.ServiceEngineRepository;
import cn.superhuang.data.scalpel.business.service.repository.ScriptDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.repository.SqlDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.repository.SqlDataServiceModelReferenceRepository;
import cn.superhuang.data.scalpel.business.service.repository.SqlDataServiceParameterRepository;
import cn.superhuang.data.scalpel.business.service.repository.StandardDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.repository.SpatialDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.web.request.CreateDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.request.PublishDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.request.ScriptDataServiceDefinitionRequest;
import cn.superhuang.data.scalpel.business.service.web.request.ScriptRequestExampleRequest;
import cn.superhuang.data.scalpel.business.service.web.request.ScriptRequestParameterRequest;
import cn.superhuang.data.scalpel.business.service.web.request.SqlDataServiceDefinitionRequest;
import cn.superhuang.data.scalpel.business.service.web.request.SqlServiceTestRequest;
import cn.superhuang.data.scalpel.business.service.web.request.StandardDataServiceDefinitionRequest;
import cn.superhuang.data.scalpel.business.service.web.request.SpatialDataServiceDefinitionRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateDataServiceRequest;
import cn.superhuang.data.scalpel.business.service.web.request.UpdateDataServiceDefinitionRequest;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceDetailResponse;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceSummaryResponse;
import cn.superhuang.data.scalpel.business.service.web.response.GatewayServiceBindingResponse;
import cn.superhuang.data.scalpel.business.service.web.response.GatewayDataServicePublicationResponse;
import cn.superhuang.data.scalpel.business.service.web.response.ScriptDataServiceDefinitionResponse;
import cn.superhuang.data.scalpel.business.service.web.response.ScriptRequestExampleResponse;
import cn.superhuang.data.scalpel.business.service.web.response.ScriptRequestParameterResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SqlDataServiceDefinitionResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SqlServiceTestProblem;
import cn.superhuang.data.scalpel.business.service.web.response.SqlServiceTestResponse;
import cn.superhuang.data.scalpel.business.service.web.response.StandardDataServiceDefinitionResponse;
import cn.superhuang.data.scalpel.business.service.web.response.SpatialDataServiceDefinitionResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import cn.superhuang.data.scalpel.contract.service.EngineDeploymentStatus;
import cn.superhuang.data.scalpel.contract.service.ServiceDefinitionSnapshot;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ServiceDeploymentResponse;
import cn.superhuang.data.scalpel.contract.service.ServiceFieldDefinition;
import cn.superhuang.data.scalpel.contract.service.ServiceUndeploymentRequest;
import cn.superhuang.data.scalpel.contract.service.ScriptServiceDefinition;
import cn.superhuang.data.scalpel.contract.service.SqlServiceDefinition;
import cn.superhuang.data.scalpel.contract.service.SqlServiceParameterDefinition;
import cn.superhuang.data.scalpel.contract.service.StandardServiceDefinition;
import cn.superhuang.data.scalpel.contract.service.SpatialServiceDefinition;
import cn.superhuang.data.scalpel.contract.type.GeometryKind;
import cn.superhuang.data.scalpel.contract.type.CoordinateDimension;
import cn.superhuang.data.scalpel.contract.type.PlatformDataType;
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
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Control-plane CRUD and deployment lifecycle for all data service types. */
@Service
public class DataServiceManagementService {

    private final DataServiceRepository repository;
    private final DataServiceDeploymentRepository deploymentRepository;
    private final GatewayServiceBindingRepository gatewayBindingRepository;
    private final ApiServiceSubscriptionRepository subscriptionRepository;
    private final StandardDataServiceDefinitionRepository standardDefinitionRepository;
    private final SqlDataServiceDefinitionRepository sqlDefinitionRepository;
    private final ScriptDataServiceDefinitionRepository scriptDefinitionRepository;
    private final SpatialDataServiceDefinitionRepository spatialDefinitionRepository;
    private final SqlDataServiceModelReferenceRepository sqlModelReferenceRepository;
    private final SqlDataServiceParameterRepository sqlParameterRepository;
    private final ServiceEngineRepository engineRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository fieldRepository;
    private final DataSourceRepository dataSourceRepository;
    private final DirectoryService directoryService;
    private final ModelPhysicalTablePort physicalTablePort;
    private final ServiceEngineClient engineClient;
    private final GeoServerClient geoServerClient;
    private final SpatialDataServiceStyleService spatialStyleService;
    private final DataServiceGatewayPublicationService gatewayPublicationService;
    private final ServiceEngineDataSourceRegistrationService dataSourceRegistrationService;
    private final ServiceEngineAccessPolicyService accessPolicyService;
    private final SqlServiceDefinitionInspector sqlInspector;
    private final DialectRegistry dialectRegistry;
    private final SearchEngine searchEngine;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readTransactionTemplate;

    public DataServiceManagementService(
            DataServiceRepository repository,
            DataServiceDeploymentRepository deploymentRepository,
            GatewayServiceBindingRepository gatewayBindingRepository,
            ApiServiceSubscriptionRepository subscriptionRepository,
            StandardDataServiceDefinitionRepository standardDefinitionRepository,
            SqlDataServiceDefinitionRepository sqlDefinitionRepository,
            ScriptDataServiceDefinitionRepository scriptDefinitionRepository,
            SpatialDataServiceDefinitionRepository spatialDefinitionRepository,
            SqlDataServiceModelReferenceRepository sqlModelReferenceRepository,
            SqlDataServiceParameterRepository sqlParameterRepository,
            ServiceEngineRepository engineRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository fieldRepository,
            DataSourceRepository dataSourceRepository,
            DirectoryService directoryService,
            ModelPhysicalTablePort physicalTablePort,
            ServiceEngineClient engineClient,
            GeoServerClient geoServerClient,
            SpatialDataServiceStyleService spatialStyleService,
            DataServiceGatewayPublicationService gatewayPublicationService,
            ServiceEngineDataSourceRegistrationService dataSourceRegistrationService,
            ServiceEngineAccessPolicyService accessPolicyService,
            SqlServiceDefinitionInspector sqlInspector,
            DialectRegistry dialectRegistry,
            SearchEngine searchEngine,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager
    ) {
        this.repository = repository;
        this.deploymentRepository = deploymentRepository;
        this.gatewayBindingRepository = gatewayBindingRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.standardDefinitionRepository = standardDefinitionRepository;
        this.sqlDefinitionRepository = sqlDefinitionRepository;
        this.scriptDefinitionRepository = scriptDefinitionRepository;
        this.spatialDefinitionRepository = spatialDefinitionRepository;
        this.sqlModelReferenceRepository = sqlModelReferenceRepository;
        this.sqlParameterRepository = sqlParameterRepository;
        this.engineRepository = engineRepository;
        this.modelRepository = modelRepository;
        this.fieldRepository = fieldRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.directoryService = directoryService;
        this.physicalTablePort = physicalTablePort;
        this.engineClient = engineClient;
        this.geoServerClient = geoServerClient;
        this.spatialStyleService = spatialStyleService;
        this.gatewayPublicationService = gatewayPublicationService;
        this.dataSourceRegistrationService = dataSourceRegistrationService;
        this.accessPolicyService = accessPolicyService;
        this.sqlInspector = sqlInspector;
        this.dialectRegistry = dialectRegistry;
        this.searchEngine = searchEngine;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readTransactionTemplate.setReadOnly(true);
    }

    @Transactional(readOnly = true)
    public PageResponse<DataServiceSummaryResponse> search(SearchRequest request) {
        Page<DataService> page = searchEngine.search(request, DataService.class, repository);
        List<DataService> services = page.getContent();
        Map<UUID, DataServiceDeployment> deployments = deploymentsByServiceId(services);
        Map<UUID, List<GatewayServiceBinding>> gatewayBindings = gatewayBindingsByServiceId(services);
        Map<UUID, DefinitionSummary> definitions = definitionSummariesByServiceId(services);
        return new PageResponse<>(
                services.stream().map(service -> summary(
                        service,
                        deployments.get(service.getId()),
                        gatewayBindings.getOrDefault(service.getId(), List.of()),
                        definitions.get(service.getId())
                )).toList(),
                page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public DataServiceDetailResponse get(UUID id) {
        return detail(
                requireService(id),
                deploymentRepository.findByDataServiceId(id).orElse(null),
                gatewayBindingRepository.findAllByDataServiceId(id)
        );
    }

    @Transactional(readOnly = true)
    public List<GatewayDataServicePublicationResponse> publishedGatewayServices(DataServiceAccessMode accessMode) {
        if (accessMode == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "网关访问方式不能为空");
        List<GatewayServiceBinding> bindings = gatewayBindingRepository.findAllByPublicationStatusAndAccessMode(
                cn.superhuang.data.scalpel.business.service.gateway.domain.GatewayServicePublicationStatus.PUBLISHED,
                accessMode
        );
        Map<UUID, DataService> services = repository.findAllByIdIn(
                bindings.stream().map(GatewayServiceBinding::getDataServiceId).toList()
        ).stream().collect(Collectors.toMap(DataService::getId, Function.identity()));
        return bindings.stream()
                .map(binding -> new GatewayDataServicePublicationResponse(
                        binding.getDataServiceId(),
                        services.get(binding.getDataServiceId()) == null ? null : services.get(binding.getDataServiceId()).getCode(),
                        services.get(binding.getDataServiceId()) == null ? null : services.get(binding.getDataServiceId()).getName(),
                        binding.getGatewayRoutePath(), binding.getAccessMode(), binding.getGatewayUrl()
                ))
                .filter(item -> item.dataServiceCode() != null)
                .sorted(java.util.Comparator.comparing(GatewayDataServicePublicationResponse::dataServiceCode))
                .toList();
    }

    @Transactional
    public DataServiceDetailResponse create(CreateDataServiceRequest request) {
        boolean definitionPresent = validateDefinitionShape(
                request.type(), request.standardDefinition(), request.sqlDefinition(), request.scriptDefinition(),
                request.spatialDefinition(), false
        );
        String code = request.code().trim().toLowerCase(Locale.ROOT);
        if (repository.existsByCode(code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务编码已存在");
        }
        directoryService.validateAssignment(DirectoryScope.DATA_SERVICE, request.directoryId());
        requireEngineCompatibility(request.engineId(), request.type());
        String contextPath = contextPath(request.type(), request.contextPath());
        if (contextPath != null && repository.existsByEngineIdAndContextPath(request.engineId(), contextPath)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前服务引擎上的服务路径已存在");
        }
        if (definitionPresent) {
            validateDefinitionSource(
                    request.type(), request.standardDefinition(), request.sqlDefinition(),
                    request.scriptDefinition(), request.spatialDefinition(), request.engineId()
            );
        }
        DataService service = repository.saveAndFlush(DataService.create(
                code, request.name(), request.directoryId(), request.type(), request.engineId(),
                contextPath, request.description()
        ));
        if (definitionPresent) {
            saveNewDefinition(
                    service.getId(), request.type(), request.standardDefinition(),
                    request.sqlDefinition(), request.scriptDefinition(), request.spatialDefinition()
            );
        }
        return detail(service, null, List.of());
    }

    @Transactional
    public DataServiceDetailResponse update(UUID id, UpdateDataServiceRequest request) {
        DataService service = requireServiceForUpdate(id);
        requireModifiable(service);
        if (request.type() != service.getType()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务类型创建后不能修改");
        }
        boolean definitionPresent = validateDefinitionShape(
                request.type(), request.standardDefinition(), request.sqlDefinition(), request.scriptDefinition(),
                request.spatialDefinition(), false
        );
        directoryService.validateAssignment(DirectoryScope.DATA_SERVICE, request.directoryId());
        requireEngineCompatibility(request.engineId(), request.type());
        String contextPath = contextPath(request.type(), request.contextPath());
        if (contextPath != null && repository.existsByEngineIdAndContextPathAndIdNot(request.engineId(), contextPath, id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前服务引擎上的服务路径已存在");
        }
        if (definitionPresent) {
            validateDefinitionSource(
                    request.type(), request.standardDefinition(), request.sqlDefinition(),
                    request.scriptDefinition(), request.spatialDefinition(), request.engineId()
            );
        }
        service.update(
                request.name(), request.directoryId(), request.engineId(), contextPath, request.description()
        );
        if (definitionPresent) {
            upsertDefinition(
                    id, request.type(), request.standardDefinition(), request.sqlDefinition(),
                    request.scriptDefinition(), request.spatialDefinition()
            );
        }
        return detail(
                repository.saveAndFlush(service),
                deploymentRepository.findByDataServiceId(id).orElse(null),
                gatewayBindingRepository.findAllByDataServiceId(id)
        );
    }

    @Transactional
    public DataServiceDetailResponse updateDefinition(UUID id, UpdateDataServiceDefinitionRequest request) {
        DataService service = requireServiceForUpdate(id);
        requireModifiable(service);
        validateDefinitionShape(
                service.getType(), request.standardDefinition(), request.sqlDefinition(), request.scriptDefinition(),
                request.spatialDefinition(), true
        );
        validateDefinitionSource(
                service.getType(), request.standardDefinition(), request.sqlDefinition(),
                request.scriptDefinition(), request.spatialDefinition(), service.getEngineId()
        );
        upsertDefinition(
                id, service.getType(), request.standardDefinition(), request.sqlDefinition(),
                request.scriptDefinition(), request.spatialDefinition()
        );
        return detail(
                repository.saveAndFlush(service),
                deploymentRepository.findByDataServiceId(id).orElse(null),
                gatewayBindingRepository.findAllByDataServiceId(id)
        );
    }

    public SqlServiceTestResponse testSql(SqlServiceTestRequest request) {
        DataSource dataSource = requireTransactionResult(readTransactionTemplate.execute(
                status -> {
                    DataSource selected = requireSqlDataSource(request.dataSourceId());
                    requireSqlModels(selected.getId(), request.modelIds());
                    return selected;
                }
        ));
        return sqlInspector.test(
                dataSource,
                request.sqlText(),
                request.parameters(),
                request.arguments(),
                request.previewSize() == null ? 20 : request.previewSize()
        );
    }

    public DataServiceDetailResponse enable(UUID id) {
        EnablePreparation preparation = requireTransactionResult(
                readTransactionTemplate.execute(status -> prepareEnable(id))
        );
        ServiceDefinitionSnapshot definition = inspectForEnable(preparation);
        EnableCommand command = requireTransactionResult(
                transactionTemplate.execute(status -> beginEnable(preparation, definition))
        );
        String failure = null;
        try {
            if (command.engine().getType() == ServiceEngineType.GEOSERVER) {
                if (command.styleDeployment().sldText() != null) {
                    geoServerClient.upsertStyle(
                            command.engine(), command.styleDeployment().styleName(), command.styleDeployment().sldText()
                    );
                }
                geoServerClient.upsertLayer(command.engine(), command.layerSpec());
            } else {
                ServiceDeploymentResponse response = engineClient.deploy(command.engine(), command.request());
                if (response == null || response.status() != EngineDeploymentStatus.DEPLOYED) {
                    throw new IllegalStateException("服务引擎未确认部署结果");
                }
            }
        } catch (RuntimeException exception) {
            failure = safeMessage(exception);
        }
        String finalFailure = failure;
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeEnable(
                        id, command.revision(), command.operationId(),
                        command.styleDeployment() == null ? null : command.styleDeployment().styleVersion(),
                        finalFailure
                )
        ));
    }

    public DataServiceDetailResponse publish(UUID id, PublishDataServiceRequest request) {
        gatewayPublicationService.publish(id, request.gatewayRoutePath(), request.accessMode());
        return requireTransactionResult(readTransactionTemplate.execute(
                status -> detail(
                        requireService(id),
                        deploymentRepository.findByDataServiceId(id).orElse(null),
                        gatewayBindingRepository.findAllByDataServiceId(id)
                )
        ));
    }

    public DataServiceDetailResponse reconcileGateway(UUID id) {
        gatewayPublicationService.reconcile(id);
        return requireTransactionResult(readTransactionTemplate.execute(
                status -> detail(
                        requireService(id),
                        deploymentRepository.findByDataServiceId(id).orElse(null),
                        gatewayBindingRepository.findAllByDataServiceId(id)
                )
        ));
    }

    public DataServiceDetailResponse unpublish(UUID id) {
        gatewayPublicationService.unpublish(id);
        return requireTransactionResult(readTransactionTemplate.execute(
                status -> detail(
                        requireService(id),
                        deploymentRepository.findByDataServiceId(id).orElse(null),
                        gatewayBindingRepository.findAllByDataServiceId(id)
                )
        ));
    }

    public DataServiceDetailResponse disable(UUID id) {
        Optional<DataServiceGatewayPublicationService.EngineDisablePreparation> preparation =
                gatewayPublicationService.prepareDisable(id);
        if (preparation.isEmpty()) {
            return get(id);
        }
        DataServiceGatewayPublicationService.EngineDisablePreparation ready = preparation.get();
        RemovalCommand command = requireTransactionResult(readTransactionTemplate.execute(
                status -> removalCommand(ready.serviceId(), ready.engine(), ready.revision(), ready.operationId(), true)
        ));
        return executeRemoval(command, true);
    }

    public DataServiceDetailResponse cleanupDeployment(UUID id) {
        RemovalCommand command = requireTransactionResult(transactionTemplate.execute(status -> beginCleanup(id)));
        return executeRemoval(command, false);
    }

    @Transactional
    public void delete(UUID id) {
        DataService service = requireService(id);
        if (service.getStatus() == DataServiceStatus.ENABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已启用服务请先停用后再删除");
        }
        DataServiceDeployment deployment = deploymentRepository.findByDataServiceId(id).orElse(null);
        if (deployment != null && deployment.getStatus() != DataServiceDeploymentStatus.REMOVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务部署状态未确认移除，请先清理部署");
        }
        if (gatewayBindingRepository.existsByDataServiceId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务仍有网关发布状态，请先停用并清理");
        }
        if (subscriptionRepository.existsByDataServiceId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务仍有消费者订阅，请先撤回订阅");
        }
        sqlModelReferenceRepository.deleteAllByDataServiceId(id);
        sqlParameterRepository.deleteAllByDataServiceId(id);
        sqlDefinitionRepository.deleteByDataServiceId(id);
        scriptDefinitionRepository.deleteByDataServiceId(id);
        standardDefinitionRepository.deleteByDataServiceId(id);
        spatialDefinitionRepository.deleteByDataServiceId(id);
        if (deployment != null) deploymentRepository.delete(deployment);
        repository.delete(service);
    }

    private EnablePreparation prepareEnable(UUID id) {
        DataService service = requireService(id);
        if (service.getStatus() == DataServiceStatus.ENABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已启用服务不能重复启用，请先停用");
        }
        switch (service.getType()) {
            case STANDARD_TABLE -> requireStandardDefinition(service.getId());
            case SQL_QUERY -> requireSqlDefinition(service.getId());
            case SCRIPT_API -> requireScriptDefinition(service.getId());
            case SPATIAL_SERVICE -> requireSpatialDefinition(service.getId());
        }
        ServiceEngine engine = requireEnabledEngine(service.getEngineId());
        requireEngineCompatibility(engine.getId(), service.getType());
        if (engine.getType() == ServiceEngineType.DATASCALPEL) {
            accessPolicyService.requireReadyPolicy(engine.getId());
        }
        return switch (service.getType()) {
            case STANDARD_TABLE -> prepareStandardEnable(service, engine);
            case SQL_QUERY -> prepareSqlEnable(service, engine);
            case SCRIPT_API -> prepareScriptEnable(service, engine);
            case SPATIAL_SERVICE -> prepareSpatialEnable(service, engine);
        };
    }

    private EnablePreparation prepareStandardEnable(DataService service, ServiceEngine engine) {
        StandardDataServiceDefinition design = requireStandardDefinition(service.getId());
        DataModel model = requireModel(design.getModelId());
        if (model.getStatus() != DataModelStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "只能启用绑定已发布模型的数据服务");
        }
        DataSource dataSource = requireStorageDataSource(model.getStorageDataSourceId());
        dataSourceRegistrationService.requireReadyRegistration(service.getEngineId(), dataSource.getId());
        List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId());
        if (fields.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "模型未定义字段");
        StandardServiceDefinition definition = new StandardServiceDefinition(
                1,
                model.getCatalogName(),
                model.getSchemaName(),
                model.getPhysicalTableName(),
                fields.stream().map(field -> new ServiceFieldDefinition(
                        field.getCode(), field.getCode(), field.getFieldType(), field.isNullable(), field.isPrimaryKey()
                )).toList()
        );
        return EnablePreparation.standard(
                snapshot(service), engine, dataSource, design.getVersion(), model.getUpdatedAt(), model, fields, definition
        );
    }

    private EnablePreparation prepareSqlEnable(DataService service, ServiceEngine engine) {
        SqlDataServiceDefinition design = requireSqlDefinition(service.getId());
        DataSource dataSource = requireSqlDataSource(design.getDataSourceId());
        dataSourceRegistrationService.requireReadyRegistration(service.getEngineId(), dataSource.getId());
        List<UUID> modelIds = sqlModelIds(service.getId());
        requireSqlModels(dataSource.getId(), modelIds);
        List<SqlServiceParameterDefinition> parameters = parameterDefinitions(service.getId());
        requireLocalSqlDefinition(dataSource, design.getSqlText(), parameters);
        return EnablePreparation.sql(
                snapshot(service), engine, dataSource, design.getVersion(), design.getSqlText(), modelIds, parameters
        );
    }

    private EnablePreparation prepareScriptEnable(DataService service, ServiceEngine engine) {
        ScriptDataServiceDefinition design = requireScriptDefinition(service.getId());
        DataSource dataSource = requireScriptDataSource(design.getDataSourceId());
        dataSourceRegistrationService.requireReadyRegistration(service.getEngineId(), dataSource.getId());
        return EnablePreparation.script(
                snapshot(service), engine, dataSource, design.getVersion(), design.getScript()
        );
    }

    private EnablePreparation prepareSpatialEnable(DataService service, ServiceEngine engine) {
        SpatialDataServiceDefinition design = requireSpatialDefinition(service.getId());
        SpatialModel spatialModel = requireSpatialModel(design.getModelId(), engine.getId());
        dataSourceRegistrationService.requireReadyRegistration(engine.getId(), spatialModel.dataSource().getId());
        SpatialServiceDefinition definition = new SpatialServiceDefinition(
                1,
                spatialModel.model().getCatalogName(),
                spatialModel.model().getSchemaName(),
                spatialModel.model().getPhysicalTableName(),
                spatialModel.geometryField().getCode(),
                spatialModel.geometryField().getGeometry().kind(),
                spatialModel.geometryField().getGeometry().crs().code(),
                spatialModel.primaryKeyField().getCode(),
                GeoServerClient.layerName(service.getCode()),
                service.getName()
        );
        return EnablePreparation.spatial(
                snapshot(service), engine, spatialModel.dataSource(), design.getVersion(),
                spatialModel.model().getUpdatedAt(), spatialModel.model(), spatialModel.fields(), definition
        );
    }

    private ServiceDefinitionSnapshot inspectForEnable(EnablePreparation preparation) {
        if (preparation.service().type() == DataServiceType.STANDARD_TABLE) {
            ModelPhysicalTableInspection inspection = physicalTablePort.inspect(
                    preparation.dataSource(), preparation.model(), preparation.modelFields()
            );
            if (inspection.state() != PhysicalTableState.MATCHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "模型物理表未就绪：" + inspection.message());
            }
            return ServiceDefinitionSnapshot.standard(preparation.standardDefinition());
        }
        if (preparation.service().type() == DataServiceType.SCRIPT_API) {
            return ServiceDefinitionSnapshot.script(new ScriptServiceDefinition(preparation.script()));
        }
        if (preparation.service().type() == DataServiceType.SPATIAL_SERVICE) {
            ModelPhysicalTableInspection inspection = physicalTablePort.inspect(
                    preparation.dataSource(), preparation.model(), preparation.modelFields()
            );
            if (inspection.state() != PhysicalTableState.MATCHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "空间模型物理表未就绪：" + inspection.message());
            }
            return ServiceDefinitionSnapshot.spatial(preparation.spatialDefinition());
        }
        SqlServiceInspection inspection = sqlInspector.inspect(
                preparation.dataSource(), preparation.sqlText(), preparation.sqlParameters()
        );
        if (!inspection.valid()) {
            SqlServiceTestProblem problem = inspection.problems().getFirst();
            throw new ResponseStatusException(HttpStatus.CONFLICT, problem.message());
        }
        return ServiceDefinitionSnapshot.sql(new SqlServiceDefinition(
                1, inspection.jdbcSql(), inspection.bindingOrder(), preparation.sqlParameters(), inspection.resultFields()
        ));
    }

    private EnableCommand beginEnable(EnablePreparation preparation, ServiceDefinitionSnapshot definition) {
        DataService service = requireServiceForUpdate(preparation.service().id());
        verifyUnchanged(preparation, service);
        ServiceEngine engine = requireEnabledEngine(service.getEngineId());
        DataSource dataSource = switch (service.getType()) {
            case STANDARD_TABLE -> requireStorageDataSource(preparation.dataSource().getId());
            case SQL_QUERY -> requireSqlDataSource(preparation.dataSource().getId());
            case SCRIPT_API -> requireScriptDataSource(preparation.dataSource().getId());
            case SPATIAL_SERVICE -> requirePostGisDataSource(preparation.dataSource().getId());
        };
        if (!Objects.equals(dataSource.getUpdatedAt(), preparation.dataSource().getUpdatedAt())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据源定义已发生变化，请重新启用");
        }
        dataSourceRegistrationService.requireReadyRegistration(service.getEngineId(), dataSource.getId());
        List<UUID> digestModelIds = List.of();
        if (service.getType() == DataServiceType.SQL_QUERY) {
            digestModelIds = sqlModelIds(service.getId());
            if (!digestModelIds.equals(preparation.sqlModelIds())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SQL 服务关联模型已发生变化，请重新启用");
            }
            requireSqlModels(dataSource.getId(), digestModelIds);
        }

        String definitionJson = write(definition);
        String definitionDigest = digest(service.getId() + "|" + service.getEngineId() + "|" + service.getContextPath()
                + "|" + dataSource.getId() + "|" + digestModelIds + "|" + definitionJson);
        DataServiceDeployment deployment = deploymentRepository.findByDataServiceId(service.getId()).orElse(null);
        long revision;
        if (deployment != null
                && (deployment.getStatus() == DataServiceDeploymentStatus.PENDING
                || deployment.getStatus() == DataServiceDeploymentStatus.FAILED)
                && definitionDigest.equals(deployment.getDefinitionDigest())) {
            revision = deployment.getRevision();
            deployment.begin(revision, service.getEngineId(), definitionDigest, definitionJson);
        } else {
            if (deployment != null && deployment.getStatus() != DataServiceDeploymentStatus.REMOVED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "已有未清理的部署状态，不能启用不同定义");
            }
            revision = service.nextRevision();
            if (deployment == null) {
                deployment = DataServiceDeployment.pending(
                        service.getId(), revision, service.getEngineId(), definitionDigest, definitionJson
                );
            } else {
                deployment.begin(revision, service.getEngineId(), definitionDigest, definitionJson);
            }
        }
        repository.saveAndFlush(service);
        deploymentRepository.saveAndFlush(deployment);
        if (service.getType() == DataServiceType.SPATIAL_SERVICE) {
            SpatialServiceDefinition spatial = definition.spatialDefinition();
            SpatialDataServiceStyleService.StyleDeployment style = spatialStyleService.beginDeployment(
                    service.getId(), service.getCode()
            );
            return new EnableCommand(
                    engine,
                    null,
                    new GeoServerClient.LayerSpec(
                            dataSource.getId(), service.getCode(), spatial.title(), spatial.table(),
                            spatial.geometryColumn(), spatial.epsg(), style.styleName()
                    ),
                    style,
                    revision, deployment.getOperationId()
            );
        }
        return new EnableCommand(
                engine,
                new ServiceDeploymentRequest(
                        service.getId(), service.getCode(), service.getContextPath(),
                        definitionDigest, definition, dataSource.getId()
                ),
                null,
                null,
                revision, deployment.getOperationId()
        );
    }

    private void verifyUnchanged(EnablePreparation preparation, DataService service) {
        if (service.getStatus() == DataServiceStatus.ENABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已启用服务不能重复启用，请先停用");
        }
        if (!Objects.equals(service.getUpdatedAt(), preparation.service().updatedAt())
                || service.getType() != preparation.service().type()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务定义已发生变化，请重新启用");
        }
        if (service.getType() == DataServiceType.STANDARD_TABLE) {
            StandardDataServiceDefinition definition = requireStandardDefinition(service.getId());
            DataModel model = requireModel(definition.getModelId());
            if (definition.getVersion() != preparation.definitionVersion()
                    || !Objects.equals(model.getUpdatedAt(), preparation.modelUpdatedAt())
                    || model.getStatus() != DataModelStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "模型或标准服务定义已发生变化，请重新启用");
            }
        } else if (service.getType() == DataServiceType.SQL_QUERY) {
            SqlDataServiceDefinition definition = requireSqlDefinition(service.getId());
            List<UUID> modelIds = sqlModelIds(service.getId());
            if (definition.getVersion() != preparation.definitionVersion()
                    || !modelIds.equals(preparation.sqlModelIds())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SQL 服务定义已发生变化，请重新启用");
            }
            requireSqlModels(definition.getDataSourceId(), modelIds);
        } else if (service.getType() == DataServiceType.SCRIPT_API) {
            ScriptDataServiceDefinition definition = requireScriptDefinition(service.getId());
            if (definition.getVersion() != preparation.definitionVersion()
                    || !Objects.equals(definition.getScript(), preparation.script())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "脚本服务定义已发生变化，请重新启用");
            }
        } else {
            SpatialDataServiceDefinition definition = requireSpatialDefinition(service.getId());
            SpatialModel spatialModel = requireSpatialModel(definition.getModelId(), service.getEngineId());
            if (definition.getVersion() != preparation.definitionVersion()
                    || !Objects.equals(spatialModel.model().getUpdatedAt(), preparation.modelUpdatedAt())
                    || spatialModel.model().getStatus() != DataModelStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "空间模型或空间服务定义已发生变化，请重新启用");
            }
        }
    }

    private DataServiceDetailResponse completeEnable(UUID id, long revision, UUID operationId, Integer styleVersion, String failure) {
        DataService service = requireServiceForUpdate(id);
        DataServiceDeployment deployment = requireDeployment(id);
        requireRevision(service, deployment, revision);
        if (!java.util.Objects.equals(operationId, deployment.getOperationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务部署操作已被后续操作替代");
        }
        if (service.getType() == DataServiceType.SPATIAL_SERVICE && styleVersion != null) {
            spatialStyleService.completeDeployment(id, styleVersion, failure);
        }
        if (failure == null) {
            deployment.deployed();
            service.markEnabled();
        } else {
            deployment.failed(failure);
        }
        deploymentRepository.saveAndFlush(deployment);
        return detail(
                repository.saveAndFlush(service),
                deployment,
                gatewayBindingRepository.findAllByDataServiceId(id)
        );
    }

    private RemovalCommand beginCleanup(UUID id) {
        DataService service = requireServiceForUpdate(id);
        if (service.getStatus() == DataServiceStatus.ENABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已启用服务请使用停用操作");
        }
        DataServiceDeployment deployment = requireDeployment(id);
        if (deployment.getStatus() != DataServiceDeploymentStatus.FAILED
                && deployment.getStatus() != DataServiceDeploymentStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前部署状态不需要清理");
        }
        ServiceEngine engine = requireEngine(deployment.getEngineId());
        deployment.beginRemoval();
        deploymentRepository.saveAndFlush(deployment);
        return removalCommand(id, engine, deployment.getRevision(), deployment.getOperationId(), false);
    }

    private DataServiceDetailResponse executeRemoval(RemovalCommand command, boolean disableService) {
        String failure = null;
        try {
            if (command.engine().getType() == ServiceEngineType.GEOSERVER) {
                geoServerClient.removeLayer(command.engine(), command.dataSourceId(), command.serviceCode());
                geoServerClient.removeStyle(command.engine(), command.serviceCode());
            } else {
                ServiceDeploymentResponse response = engineClient.remove(
                        command.engine(), new ServiceUndeploymentRequest(command.serviceId())
                );
                if (response == null || response.status() != EngineDeploymentStatus.REMOVED) {
                    throw new IllegalStateException("服务引擎未确认停用结果");
                }
            }
        } catch (RuntimeException exception) {
            failure = safeMessage(exception);
        }
        String finalFailure = failure;
        return requireTransactionResult(transactionTemplate.execute(
                status -> completeRemoval(command.serviceId(), command.revision(), command.operationId(), finalFailure, disableService)
        ));
    }

    private DataServiceDetailResponse completeRemoval(UUID id, long revision, UUID operationId, String failure, boolean disableService) {
        DataService service = requireServiceForUpdate(id);
        DataServiceDeployment deployment = requireDeployment(id);
        requireRevision(service, deployment, revision);
        if (!java.util.Objects.equals(operationId, deployment.getOperationId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务部署操作已被后续操作替代");
        }
        if (failure == null) {
            deployment.removed();
            if (service.getType() == DataServiceType.SPATIAL_SERVICE) spatialStyleService.markRemoved(id);
            if (disableService) service.markDisabled();
        } else {
            deployment.failed(failure);
        }
        deploymentRepository.saveAndFlush(deployment);
        return detail(
                repository.saveAndFlush(service),
                deployment,
                gatewayBindingRepository.findAllByDataServiceId(id)
        );
    }

    private RemovalCommand removalCommand(
            UUID serviceId,
            ServiceEngine engine,
            long revision,
            UUID operationId,
            boolean disableService
    ) {
        DataService service = requireService(serviceId);
        if (engine.getType() == ServiceEngineType.GEOSERVER) {
            SpatialDataServiceDefinition definition = requireSpatialDefinition(serviceId);
            DataModel model = requireModel(definition.getModelId());
            return new RemovalCommand(
                    serviceId, engine, revision, operationId, disableService, service.getCode(), model.getStorageDataSourceId()
            );
        }
        return new RemovalCommand(serviceId, engine, revision, operationId, disableService, service.getCode(), null);
    }

    private void saveNewDefinition(
            UUID serviceId,
            DataServiceType type,
            StandardDataServiceDefinitionRequest standard,
            SqlDataServiceDefinitionRequest sql,
            ScriptDataServiceDefinitionRequest script,
            SpatialDataServiceDefinitionRequest spatial
    ) {
        if (type == DataServiceType.STANDARD_TABLE) {
            standardDefinitionRepository.saveAndFlush(StandardDataServiceDefinition.create(serviceId, standard.modelId()));
        } else if (type == DataServiceType.SQL_QUERY) {
            sqlDefinitionRepository.saveAndFlush(SqlDataServiceDefinition.create(serviceId, sql.dataSourceId(), sql.sqlText()));
            replaceModelReferences(serviceId, sql.modelIds());
            replaceParameters(serviceId, sql.parameters());
        } else if (type == DataServiceType.SCRIPT_API) {
            scriptDefinitionRepository.saveAndFlush(
                    ScriptDataServiceDefinition.create(
                            serviceId,
                            script.dataSourceId(),
                            script.script(),
                            writeScriptExamples(normalizeScriptExamples(script.examples()))
                    )
            );
        } else {
            SpatialDataServiceDefinition definition = SpatialDataServiceDefinition.create(serviceId, spatial.modelId());
            initializeSpatialStyleDocument(definition);
            spatialDefinitionRepository.saveAndFlush(definition);
        }
    }

    private void updateDefinition(
            UUID serviceId,
            DataServiceType type,
            StandardDataServiceDefinitionRequest standard,
            SqlDataServiceDefinitionRequest sql,
            ScriptDataServiceDefinitionRequest script,
            SpatialDataServiceDefinitionRequest spatial
    ) {
        if (type == DataServiceType.STANDARD_TABLE) {
            StandardDataServiceDefinition definition = requireStandardDefinition(serviceId);
            definition.update(standard.modelId());
            standardDefinitionRepository.saveAndFlush(definition);
            return;
        }
        if (type == DataServiceType.SCRIPT_API) {
            ScriptDataServiceDefinition definition = requireScriptDefinition(serviceId);
            definition.update(
                    script.dataSourceId(),
                    script.script(),
                    writeScriptExamples(normalizeScriptExamples(script.examples()))
            );
            scriptDefinitionRepository.saveAndFlush(definition);
            return;
        }
        if (type == DataServiceType.SPATIAL_SERVICE) {
            SpatialDataServiceDefinition definition = requireSpatialDefinition(serviceId);
            boolean modelChanged = !definition.getModelId().equals(spatial.modelId());
            definition.update(spatial.modelId());
            if (modelChanged) initializeSpatialStyleDocument(definition);
            spatialDefinitionRepository.saveAndFlush(definition);
            return;
        }
        SqlDataServiceDefinition definition = requireSqlDefinition(serviceId);
        List<SqlServiceParameterDefinition> existingParameters = parameterDefinitions(serviceId);
        List<UUID> existingModelIds = sqlModelIds(serviceId);
        boolean parametersChanged = !existingParameters.equals(sql.parameters());
        boolean modelsChanged = !existingModelIds.equals(sql.modelIds());
        definition.update(sql.dataSourceId(), sql.sqlText(), parametersChanged || modelsChanged);
        sqlDefinitionRepository.saveAndFlush(definition);
        if (parametersChanged) replaceParameters(serviceId, sql.parameters());
        if (modelsChanged) replaceModelReferences(serviceId, sql.modelIds());
    }

    private void upsertDefinition(
            UUID serviceId,
            DataServiceType type,
            StandardDataServiceDefinitionRequest standard,
            SqlDataServiceDefinitionRequest sql,
            ScriptDataServiceDefinitionRequest script,
            SpatialDataServiceDefinitionRequest spatial
    ) {
        boolean configured = switch (type) {
            case STANDARD_TABLE -> standardDefinitionRepository.findByDataServiceId(serviceId).isPresent();
            case SQL_QUERY -> sqlDefinitionRepository.findByDataServiceId(serviceId).isPresent();
            case SCRIPT_API -> scriptDefinitionRepository.findByDataServiceId(serviceId).isPresent();
            case SPATIAL_SERVICE -> spatialDefinitionRepository.findByDataServiceId(serviceId).isPresent();
        };
        if (configured) updateDefinition(serviceId, type, standard, sql, script, spatial);
        else saveNewDefinition(serviceId, type, standard, sql, script, spatial);
    }

    private void replaceModelReferences(UUID serviceId, List<UUID> modelIds) {
        sqlModelReferenceRepository.deleteAllByDataServiceId(serviceId);
        sqlModelReferenceRepository.flush();
        List<SqlDataServiceModelReference> references = new ArrayList<>(modelIds.size());
        for (int index = 0; index < modelIds.size(); index++) {
            references.add(SqlDataServiceModelReference.create(serviceId, modelIds.get(index), index));
        }
        sqlModelReferenceRepository.saveAllAndFlush(references);
    }

    private void replaceParameters(UUID serviceId, List<SqlServiceParameterDefinition> parameters) {
        sqlParameterRepository.deleteAllByDataServiceId(serviceId);
        sqlParameterRepository.flush();
        List<SqlDataServiceParameter> entities = new ArrayList<>(parameters.size());
        for (int index = 0; index < parameters.size(); index++) {
            entities.add(SqlDataServiceParameter.create(serviceId, parameters.get(index), index));
        }
        sqlParameterRepository.saveAllAndFlush(entities);
    }

    private boolean validateDefinitionShape(
            DataServiceType type,
            StandardDataServiceDefinitionRequest standard,
            SqlDataServiceDefinitionRequest sql,
            ScriptDataServiceDefinitionRequest script,
            SpatialDataServiceDefinitionRequest spatial,
            boolean required
    ) {
        boolean standardPresent = standard != null;
        boolean sqlPresent = sql != null;
        boolean scriptPresent = script != null;
        boolean spatialPresent = spatial != null;
        int count = (standardPresent ? 1 : 0) + (sqlPresent ? 1 : 0)
                + (scriptPresent ? 1 : 0) + (spatialPresent ? 1 : 0);
        if (!required && count == 0) return false;
        if (count != 1
                || type == DataServiceType.STANDARD_TABLE && !standardPresent
                || type == DataServiceType.SQL_QUERY && !sqlPresent
                || type == DataServiceType.SCRIPT_API && !scriptPresent
                || type == DataServiceType.SPATIAL_SERVICE && !spatialPresent) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "数据服务类型与具体定义不匹配");
        }
        return true;
    }

    private void validateDefinitionSource(
            DataServiceType type,
            StandardDataServiceDefinitionRequest standard,
            SqlDataServiceDefinitionRequest sql,
            ScriptDataServiceDefinitionRequest script,
            SpatialDataServiceDefinitionRequest spatial,
            UUID engineId
    ) {
        if (type == DataServiceType.STANDARD_TABLE) {
            DataModel model = requireModel(standard.modelId());
            if (model.getStatus() != DataModelStatus.PUBLISHED) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "标准单表服务只能选择已发布模型");
            }
            DataSource dataSource = requireStorageDataSource(model.getStorageDataSourceId());
            if (!fieldRepository.existsByModelId(model.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "模型尚未定义字段");
            }
            dataSourceRegistrationService.requireReadyRegistration(engineId, dataSource.getId());
            return;
        }
        if (type == DataServiceType.SCRIPT_API) {
            DataSource dataSource = requireScriptDataSource(script.dataSourceId());
            dataSourceRegistrationService.requireReadyRegistration(engineId, dataSource.getId());
            return;
        }
        if (type == DataServiceType.SPATIAL_SERVICE) {
            SpatialModel model = requireSpatialModel(spatial.modelId(), engineId);
            dataSourceRegistrationService.requireReadyRegistration(engineId, model.dataSource().getId());
            return;
        }
        DataSource dataSource = requireSqlDataSource(sql.dataSourceId());
        requireSqlModels(dataSource.getId(), sql.modelIds());
        dataSourceRegistrationService.requireReadyRegistration(engineId, dataSource.getId());
        requireLocalSqlDefinition(dataSource, sql.sqlText(), sql.parameters());
    }

    private void requireLocalSqlDefinition(
            DataSource dataSource,
            String sqlText,
            List<SqlServiceParameterDefinition> parameters
    ) {
        List<SqlServiceTestProblem> problems = sqlInspector.validateLocally(dataSource, sqlText, parameters);
        if (!problems.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, problems.getFirst().message());
        }
    }

    private DataSource requireStorageDataSource(UUID id) {
        DataSource dataSource = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "模型的数据源不存在"));
        if (!dataSource.isEnabled() || !dataSource.getPurposes().contains(DataSourcePurpose.STORAGE)
                || !dataSource.getType().isJdbc()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "模型必须绑定已启用的 JDBC 数据存储");
        }
        return dataSource;
    }

    private DataSource requireSqlDataSource(UUID id) {
        DataSource dataSource = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 服务数据源不存在"));
        if (!dataSource.isEnabled() || !dataSource.getType().isJdbc()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SQL 服务必须绑定已启用的 JDBC 数据源");
        }
        var dialect = dialectRegistry.require(dataSource.getType().name());
        if (!dialect.definition().capabilities().contains(DatabaseCapability.SQL_SERVICE_QUERY)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前数据库类型未开放 SQL 服务能力");
        }
        return dataSource;
    }

    private DataSource requireScriptDataSource(UUID id) {
        DataSource dataSource = dataSourceRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "脚本服务数据源不存在"));
        if (!dataSource.isEnabled() || !dataSource.getType().isJdbc()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "脚本服务必须绑定已启用的 JDBC 数据源");
        }
        if (dataSource.getType().isTdEngine()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "TDengine 第一版不开放脚本服务，只允许通过外部模型或 Canvas 超级表输入读取"
            );
        }
        return dataSource;
    }

    private DataSource requirePostGisDataSource(UUID id) {
        DataSource dataSource = requireStorageDataSource(id);
        if (dataSource.getType() != DataSourceType.POSTGRESQL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "空间服务第一版只支持 PostgreSQL/PostGIS 数据源");
        }
        return dataSource;
    }

    private String contextPath(DataServiceType type, String value) {
        if (type == DataServiceType.SPATIAL_SERVICE) {
            if (value != null && !value.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "空间服务不配置 Engine Context Path");
            }
            return null;
        }
        try {
            return ServiceRoutePath.normalize(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    private ServiceEngine requireEngineCompatibility(UUID engineId, DataServiceType serviceType) {
        ServiceEngine engine = requireEngine(engineId);
        ServiceEngineType expected = serviceType == DataServiceType.SPATIAL_SERVICE
                ? ServiceEngineType.GEOSERVER
                : ServiceEngineType.DATASCALPEL;
        if (engine.getType() != expected) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    serviceType == DataServiceType.SPATIAL_SERVICE
                            ? "空间服务只能绑定 GeoServer 空间引擎"
                            : "普通数据服务只能绑定 DataScalpel 服务引擎"
            );
        }
        return engine;
    }

    private SpatialModel requireSpatialModel(UUID modelId, UUID engineId) {
        DataModel model = requireModel(modelId);
        if (model.getStatus() != DataModelStatus.PUBLISHED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "空间服务只能选择已发布模型");
        }
        DataSource dataSource = requirePostGisDataSource(model.getStorageDataSourceId());
        dataSourceRegistrationService.requireReadyRegistration(engineId, dataSource.getId());
        List<DataModelField> fields = fieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(model.getId());
        List<DataModelField> geometryFields = fields.stream()
                .filter(field -> field.getFieldType() == PlatformDataType.GEOMETRY)
                .toList();
        if (geometryFields.size() != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "空间模型必须恰好包含一个 Geometry 字段");
        }
        DataModelField geometryField = geometryFields.getFirst();
        if (geometryField.getGeometry() == null
                || geometryField.getGeometry().dimension() != CoordinateDimension.XY
                || !"EPSG".equalsIgnoreCase(geometryField.getGeometry().crs().authority())
                || geometryField.getGeometry().crs().code() < 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "空间模型 Geometry 字段必须使用 XY 坐标和明确的 EPSG CRS");
        }
        List<DataModelField> primaryKeys = fields.stream()
                .filter(DataModelField::isPrimaryKey)
                .filter(field -> field.getFieldType() != PlatformDataType.GEOMETRY)
                .toList();
        if (primaryKeys.size() != 1 || fields.stream().anyMatch(
                field -> field.isPrimaryKey() && field.getFieldType() == PlatformDataType.GEOMETRY
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "空间模型必须恰好包含一个非 Geometry 主键字段");
        }
        if (model.getSchemaName() == null || model.getSchemaName().isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "PostGIS 空间模型必须配置物理 Schema");
        }
        return new SpatialModel(model, dataSource, List.copyOf(fields), geometryField, primaryKeys.getFirst());
    }

    private void requireModifiable(DataService service) {
        if (service.getStatus() == DataServiceStatus.ENABLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已启用服务请先停用后再修改");
        }
        DataServiceDeployment deployment = deploymentRepository.findByDataServiceId(service.getId()).orElse(null);
        if (deployment != null && deployment.getStatus() != DataServiceDeploymentStatus.REMOVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "服务部署状态未确认移除，请先清理部署");
        }
    }

    private DataServiceDetailResponse detail(
            DataService service,
            DataServiceDeployment deployment,
            List<GatewayServiceBinding> gatewayBindings
    ) {
        StandardDataServiceDefinitionResponse standard = null;
        SqlDataServiceDefinitionResponse sql = null;
        ScriptDataServiceDefinitionResponse script = null;
        SpatialDataServiceDefinitionResponse spatial = null;
        Integer definitionVersion = null;
        if (service.getType() == DataServiceType.STANDARD_TABLE) {
            StandardDataServiceDefinition definition = standardDefinitionRepository.findByDataServiceId(service.getId())
                    .orElse(null);
            if (definition != null) {
                definitionVersion = definition.getVersion();
                standard = new StandardDataServiceDefinitionResponse(definition.getModelId(), definition.getVersion());
            }
        } else if (service.getType() == DataServiceType.SQL_QUERY) {
            SqlDataServiceDefinition definition = sqlDefinitionRepository.findByDataServiceId(service.getId())
                    .orElse(null);
            if (definition != null) {
                definitionVersion = definition.getVersion();
                sql = new SqlDataServiceDefinitionResponse(
                        definition.getDataSourceId(), sqlModelIds(service.getId()), definition.getSqlText(),
                        parameterDefinitions(service.getId()), definition.getVersion()
                );
            }
        } else if (service.getType() == DataServiceType.SCRIPT_API) {
            ScriptDataServiceDefinition definition = scriptDefinitionRepository.findByDataServiceId(service.getId())
                    .orElse(null);
            if (definition != null) {
                definitionVersion = definition.getVersion();
                script = new ScriptDataServiceDefinitionResponse(
                        definition.getDataSourceId(),
                        definition.getScript(),
                        readScriptExamples(definition.getExamplesJson()),
                        definition.getVersion()
                );
            }
        } else {
            SpatialDataServiceDefinition definition = spatialDefinitionRepository
                    .findByDataServiceId(service.getId()).orElse(null);
            if (definition != null) {
                definitionVersion = definition.getVersion();
                spatial = new SpatialDataServiceDefinitionResponse(definition.getModelId(), definition.getVersion());
            }
        }
        return new DataServiceDetailResponse(
                service.getId(), service.getCode(), service.getName(), service.getDirectoryId(), service.getType(),
                definitionVersion != null, definitionVersion,
                standard, sql, script, spatial, service.getEngineId(), service.getContextPath(),
                service.getStatus(), service.getRevision(),
                deployment == null ? null : deployment.getStatus(), deployment == null ? null : deployment.getLastError(),
                deployment == null ? null : deployment.getDeployedAt(),
                gatewayBindings.stream()
                        .sorted(java.util.Comparator.comparing(binding -> binding.getProvider().name()))
                        .map(GatewayServiceBindingResponse::from)
                        .toList(),
                service.getDescription(),
                service.getCreatedAt(), service.getUpdatedAt()
        );
    }

    private static DataServiceSummaryResponse summary(
            DataService service,
            DataServiceDeployment deployment,
            List<GatewayServiceBinding> gatewayBindings,
            DefinitionSummary definition
    ) {
        return new DataServiceSummaryResponse(
                service.getId(), service.getCode(), service.getName(), service.getDirectoryId(), service.getType(),
                definition != null, definition == null ? null : definition.version(),
                definition == null ? null : definition.sourceId(),
                definition == null ? null : definition.sourceName(),
                service.getEngineId(), service.getContextPath(),
                service.getStatus(), service.getRevision(),
                deployment == null ? null : deployment.getStatus(), deployment == null ? null : deployment.getLastError(),
                deployment == null ? null : deployment.getDeployedAt(),
                gatewayBindings.stream()
                        .sorted(java.util.Comparator.comparing(binding -> binding.getProvider().name()))
                        .map(GatewayServiceBindingResponse::from)
                        .toList(),
                service.getDescription(),
                service.getCreatedAt(), service.getUpdatedAt()
        );
    }

    private Map<UUID, DefinitionSummary> definitionSummariesByServiceId(List<DataService> services) {
        if (services.isEmpty()) return Map.of();
        List<UUID> serviceIds = services.stream().map(DataService::getId).toList();
        List<StandardDataServiceDefinition> standards = standardDefinitionRepository.findAllByDataServiceIdIn(serviceIds);
        List<SqlDataServiceDefinition> sqlDefinitions = sqlDefinitionRepository.findAllByDataServiceIdIn(serviceIds);
        List<ScriptDataServiceDefinition> scriptDefinitions =
                scriptDefinitionRepository.findAllByDataServiceIdIn(serviceIds);
        List<SpatialDataServiceDefinition> spatialDefinitions =
                spatialDefinitionRepository.findAllByDataServiceIdIn(serviceIds);
        Map<UUID, DataModel> models = modelRepository.findAllById(
                java.util.stream.Stream.concat(
                        standards.stream().map(StandardDataServiceDefinition::getModelId),
                        spatialDefinitions.stream().map(SpatialDataServiceDefinition::getModelId)
                ).distinct().toList()
        ).stream().collect(Collectors.toMap(DataModel::getId, Function.identity()));
        Map<UUID, DataSource> dataSources = dataSourceRepository.findAllById(
                java.util.stream.Stream.concat(
                        sqlDefinitions.stream().map(SqlDataServiceDefinition::getDataSourceId),
                        scriptDefinitions.stream().map(ScriptDataServiceDefinition::getDataSourceId)
                ).distinct().toList()
        ).stream().collect(Collectors.toMap(DataSource::getId, Function.identity()));
        Map<UUID, DefinitionSummary> result = new HashMap<>();
        for (StandardDataServiceDefinition definition : standards) {
            DataModel model = models.get(definition.getModelId());
            result.put(definition.getDataServiceId(), new DefinitionSummary(
                    definition.getModelId(), model == null ? "已删除" : model.getName(), definition.getVersion()
            ));
        }
        for (SqlDataServiceDefinition definition : sqlDefinitions) {
            DataSource dataSource = dataSources.get(definition.getDataSourceId());
            result.put(definition.getDataServiceId(), new DefinitionSummary(
                    definition.getDataSourceId(), dataSource == null ? "已删除" : dataSource.getName(),
                    definition.getVersion()
            ));
        }
        for (ScriptDataServiceDefinition definition : scriptDefinitions) {
            DataSource dataSource = dataSources.get(definition.getDataSourceId());
            result.put(definition.getDataServiceId(), new DefinitionSummary(
                    definition.getDataSourceId(), dataSource == null ? "已删除" : dataSource.getName(),
                    definition.getVersion()
            ));
        }
        for (SpatialDataServiceDefinition definition : spatialDefinitions) {
            DataModel model = models.get(definition.getModelId());
            result.put(definition.getDataServiceId(), new DefinitionSummary(
                    definition.getModelId(), model == null ? "已删除" : model.getName(), definition.getVersion()
            ));
        }
        return result;
    }

    private List<SqlServiceParameterDefinition> parameterDefinitions(UUID serviceId) {
        return sqlParameterRepository.findAllByDataServiceIdOrderBySortOrderAsc(serviceId).stream()
                .map(SqlDataServiceParameter::toDefinition).toList();
    }

    private List<UUID> sqlModelIds(UUID serviceId) {
        return sqlModelReferenceRepository.findAllByDataServiceIdOrderBySortOrderAsc(serviceId).stream()
                .map(SqlDataServiceModelReference::getModelId).toList();
    }

    private void requireSqlModels(UUID dataSourceId, List<UUID> modelIds) {
        if (modelIds == null || modelIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 服务至少需要关联一个模型");
        }
        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>(modelIds);
        if (uniqueIds.size() != modelIds.size() || uniqueIds.contains(null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 服务关联模型不能为空或重复");
        }
        Map<UUID, DataModel> models = modelRepository.findAllById(uniqueIds).stream()
                .collect(Collectors.toMap(DataModel::getId, Function.identity()));
        for (UUID modelId : modelIds) {
            DataModel model = models.get(modelId);
            if (model == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 服务关联模型不存在");
            }
            if (!dataSourceId.equals(model.getStorageDataSourceId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "SQL 服务关联模型不属于所选数据源");
            }
        }
    }

    private Map<UUID, DataServiceDeployment> deploymentsByServiceId(List<DataService> services) {
        if (services.isEmpty()) return Map.of();
        return deploymentRepository.findAllByDataServiceIdIn(services.stream().map(DataService::getId).toList()).stream()
                .collect(Collectors.toMap(DataServiceDeployment::getDataServiceId, Function.identity()));
    }

    private Map<UUID, List<GatewayServiceBinding>> gatewayBindingsByServiceId(List<DataService> services) {
        if (services.isEmpty()) return Map.of();
        Map<UUID, List<GatewayServiceBinding>> grouped = new HashMap<>();
        gatewayBindingRepository.findAllByDataServiceIdIn(
                services.stream().map(DataService::getId).toList()
        ).forEach(binding -> grouped.computeIfAbsent(
                binding.getDataServiceId(),
                ignored -> new ArrayList<>()
        ).add(binding));
        return grouped;
    }

    private DataService requireService(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在"));
    }

    private DataService requireServiceForUpdate(UUID id) {
        return repository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在"));
    }

    private DataServiceDeployment requireDeployment(UUID serviceId) {
        return deploymentRepository.findByDataServiceId(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "数据服务缺少部署状态"));
    }

    private StandardDataServiceDefinition requireStandardDefinition(UUID serviceId) {
        return standardDefinitionRepository.findByDataServiceId(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "数据服务定义未配置"));
    }

    private SqlDataServiceDefinition requireSqlDefinition(UUID serviceId) {
        return sqlDefinitionRepository.findByDataServiceId(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "数据服务定义未配置"));
    }

    private ScriptDataServiceDefinition requireScriptDefinition(UUID serviceId) {
        return scriptDefinitionRepository.findByDataServiceId(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "数据服务定义未配置"));
    }

    private SpatialDataServiceDefinition requireSpatialDefinition(UUID serviceId) {
        return spatialDefinitionRepository.findByDataServiceId(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "空间服务定义未配置"));
    }

    private List<ScriptRequestExampleRequest> normalizeScriptExamples(
            List<ScriptRequestExampleRequest> examples
    ) {
        if (examples == null || examples.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "脚本服务至少需要一个 Example");
        }
        Set<String> ids = new LinkedHashSet<>();
        Set<String> names = new LinkedHashSet<>();
        List<ScriptRequestExampleRequest> normalized = new ArrayList<>(examples.size());
        for (ScriptRequestExampleRequest example : examples) {
            if (example == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "脚本服务 Example 不能为空");
            }
            String id = requireExampleText(example.id(), "Example ID");
            String name = requireExampleText(example.name(), "Example 名称");
            if (!ids.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Example ID 不能重复：" + id);
            }
            if (!names.add(name)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Example 名称不能重复：" + name);
            }
            String bodyText = example.bodyText() == null ? "" : example.bodyText().trim();
            if (bodyText.isEmpty()) bodyText = "{}";
            try {
                objectMapper.readValue(bodyText, Object.class);
            } catch (JacksonException exception) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Example“" + name + "”的 Body 不是合法 JSON",
                        exception
                );
            }
            normalized.add(new ScriptRequestExampleRequest(
                    id,
                    name,
                    bodyText,
                    normalizeScriptParameters(example.query(), "Query", false),
                    normalizeScriptParameters(example.headers(), "Header", true)
            ));
        }
        return List.copyOf(normalized);
    }

    private List<ScriptRequestParameterRequest> normalizeScriptParameters(
            List<ScriptRequestParameterRequest> parameters,
            String label,
            boolean caseInsensitive
    ) {
        if (parameters == null || parameters.isEmpty()) return List.of();
        Set<String> ids = new LinkedHashSet<>();
        Set<String> keys = new LinkedHashSet<>();
        List<ScriptRequestParameterRequest> normalized = new ArrayList<>(parameters.size());
        for (ScriptRequestParameterRequest parameter : parameters) {
            if (parameter == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " 参数不能为空");
            }
            String id = requireExampleText(parameter.id(), label + " 参数 ID");
            String key = requireExampleText(parameter.key(), label + " 参数名");
            String comparisonKey = caseInsensitive ? key.toLowerCase(Locale.ROOT) : key;
            if (!ids.add(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " 参数 ID 不能重复：" + id);
            }
            if (!keys.add(comparisonKey)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + " 参数名不能重复：" + key);
            }
            normalized.add(new ScriptRequestParameterRequest(
                    id,
                    key,
                    parameter.value() == null ? "" : parameter.value()
            ));
        }
        return List.copyOf(normalized);
    }

    private String writeScriptExamples(List<ScriptRequestExampleRequest> examples) {
        try {
            return objectMapper.writeValueAsString(examples);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化脚本服务 Example", exception);
        }
    }

    private void initializeSpatialStyleDocument(SpatialDataServiceDefinition definition) {
        List<DataModelField> geometryFields = fieldRepository
                .findAllByModelIdOrderBySortOrderAscCodeAsc(definition.getModelId()).stream()
                .filter(field -> field.getGeometry() != null)
                .toList();
        if (geometryFields.size() != 1) return;
        SpatialGeometryFamily family = SpatialGeometryFamily.from(geometryFields.getFirst().getGeometry().kind());
        if (family == SpatialGeometryFamily.GENERIC) return;
        try {
            definition.initializeCartographyDocument(objectMapper.writeValueAsString(
                    SpatialStyleDocument.defaults(SpatialDataServiceStyleService.coreFamily(family))
            ));
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法初始化在线制图 V4 默认样式", exception);
        }
    }

    private List<ScriptRequestExampleResponse> readScriptExamples(String examplesJson) {
        List<ScriptRequestExampleRequest> examples;
        if (examplesJson == null || examplesJson.isBlank()) {
            examples = List.of(ScriptDataServiceDefinitionRequest.defaultExample());
        } else {
            try {
                examples = objectMapper.readValue(
                        examplesJson,
                        new TypeReference<List<ScriptRequestExampleRequest>>() { }
                );
            } catch (JacksonException exception) {
                throw new IllegalStateException("无法读取脚本服务 Example", exception);
            }
        }
        return examples.stream().map(example -> new ScriptRequestExampleResponse(
                example.id(),
                example.name(),
                example.bodyText(),
                toScriptParameterResponses(example.query()),
                toScriptParameterResponses(example.headers())
        )).toList();
    }

    private static List<ScriptRequestParameterResponse> toScriptParameterResponses(
            List<ScriptRequestParameterRequest> parameters
    ) {
        if (parameters == null) return List.of();
        return parameters.stream().map(parameter -> new ScriptRequestParameterResponse(
                parameter.id(), parameter.key(), parameter.value()
        )).toList();
    }

    private static String requireExampleText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, label + "不能为空");
        }
        return value.trim();
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
        if (!engine.isEnabled()) throw new ResponseStatusException(HttpStatus.CONFLICT, "服务引擎已停用");
        return engine;
    }

    private static void requireRevision(DataService service, DataServiceDeployment deployment, long revision) {
        if (service.getRevision() != revision || deployment.getRevision() != revision) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "数据服务部署版本已发生变化");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalStateException("无法序列化数据服务部署快照", exception);
        }
    }

    private static ServiceSnapshot snapshot(DataService service) {
        return new ServiceSnapshot(
                service.getId(), service.getCode(), service.getType(), service.getEngineId(), service.getContextPath(), service.getUpdatedAt()
        );
    }

    private static String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? "服务引擎调用失败"
                : message.substring(0, Math.min(500, message.length()));
    }

    private static String digest(String input) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte item : hash) hex.append(String.format("%02x", item));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static <T> T requireTransactionResult(T value) {
        if (value == null) throw new IllegalStateException("事务未返回数据服务处理结果");
        return value;
    }

    private record ServiceSnapshot(
            UUID id,
            String code,
            DataServiceType type,
            UUID engineId,
            String routePath,
            Instant updatedAt
    ) {
    }

    private record EnablePreparation(
            ServiceSnapshot service,
            ServiceEngine engine,
            DataSource dataSource,
            int definitionVersion,
            Instant modelUpdatedAt,
            DataModel model,
            List<DataModelField> modelFields,
            StandardServiceDefinition standardDefinition,
            String sqlText,
            List<UUID> sqlModelIds,
            List<SqlServiceParameterDefinition> sqlParameters,
            String script,
            SpatialServiceDefinition spatialDefinition
    ) {
        private static EnablePreparation standard(
                ServiceSnapshot service,
                ServiceEngine engine,
                DataSource dataSource,
                int definitionVersion,
                Instant modelUpdatedAt,
                DataModel model,
                List<DataModelField> fields,
                StandardServiceDefinition definition
        ) {
            return new EnablePreparation(
                    service, engine, dataSource, definitionVersion, modelUpdatedAt, model,
                    List.copyOf(fields), definition, null, List.of(), List.of(), null, null
            );
        }

        private static EnablePreparation sql(
                ServiceSnapshot service,
                ServiceEngine engine,
                DataSource dataSource,
                int definitionVersion,
                String sqlText,
                List<UUID> modelIds,
                List<SqlServiceParameterDefinition> parameters
        ) {
            return new EnablePreparation(
                    service, engine, dataSource, definitionVersion, null, null,
                    List.of(), null, sqlText, List.copyOf(modelIds), List.copyOf(parameters), null, null
            );
        }

        private static EnablePreparation script(
                ServiceSnapshot service,
                ServiceEngine engine,
                DataSource dataSource,
                int definitionVersion,
                String script
        ) {
            return new EnablePreparation(
                    service, engine, dataSource, definitionVersion, null, null,
                    List.of(), null, null, List.of(), List.of(), script, null
            );
        }

        private static EnablePreparation spatial(
                ServiceSnapshot service,
                ServiceEngine engine,
                DataSource dataSource,
                int definitionVersion,
                Instant modelUpdatedAt,
                DataModel model,
                List<DataModelField> fields,
                SpatialServiceDefinition definition
        ) {
            return new EnablePreparation(
                    service, engine, dataSource, definitionVersion, modelUpdatedAt, model,
                    List.copyOf(fields), null, null, List.of(), List.of(), null, definition
            );
        }
    }

    private record EnableCommand(
            ServiceEngine engine,
            ServiceDeploymentRequest request,
            GeoServerClient.LayerSpec layerSpec,
            SpatialDataServiceStyleService.StyleDeployment styleDeployment,
            long revision,
            UUID operationId
    ) {
    }

    private record RemovalCommand(
            UUID serviceId,
            ServiceEngine engine,
            long revision,
            UUID operationId,
            boolean disableService,
            String serviceCode,
            UUID dataSourceId
    ) {
    }

    private record SpatialModel(
            DataModel model,
            DataSource dataSource,
            List<DataModelField> fields,
            DataModelField geometryField,
            DataModelField primaryKeyField
    ) {
    }

    private record DefinitionSummary(UUID sourceId, String sourceName, int version) {
    }
}

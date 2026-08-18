package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.ApiResource;
import cn.superhuang.data.scalpel.business.datasource.domain.SpatialFeatureResource;
import cn.superhuang.data.scalpel.business.datasource.repository.ApiResourceRepository;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.datasource.repository.SpatialFeatureResourceRepository;
import cn.superhuang.data.scalpel.business.datasource.web.response.*;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.service.domain.*;
import cn.superhuang.data.scalpel.business.service.repository.*;
import cn.superhuang.data.scalpel.business.task.domain.*;
import cn.superhuang.data.scalpel.business.task.repository.*;
import cn.superhuang.data.scalpel.business.task.service.CanvasTaskDefinitionService;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
import cn.superhuang.data.scalpel.contract.task.*;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceAccessMode;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import cn.superhuang.data.scalpel.search.SearchEngine;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataSourceRelationQueryService {

    private final DataSourceRepository dataSourceRepository;
    private final DataModelRepository modelRepository;
    private final DataTaskRepository taskRepository;
    private final LocalSqlTaskDefinitionRepository localDefinitionRepository;
    private final LocalSqlTaskInputRepository localInputRepository;
    private final CanvasTaskDefinitionRepository canvasDefinitionRepository;
    private final TaskCanvasModelReferenceRepository canvasModelReferenceRepository;
    private final TaskDataSourceReferenceRepository dataSourceReferenceRepository;
    private final SparkJarTaskDefinitionRepository sparkJarDefinitionRepository;
    private final SparkJarTaskResourceBindingRepository sparkJarBindingRepository;
    private final CanvasTaskDefinitionService canvasDefinitionService;
    private final ApiResourceRepository apiResourceRepository;
    private final SpatialFeatureResourceRepository spatialResourceRepository;
    private final DataServiceRepository serviceRepository;
    private final StandardDataServiceDefinitionRepository standardServiceRepository;
    private final SqlDataServiceDefinitionRepository sqlServiceRepository;
    private final ScriptDataServiceDefinitionRepository scriptServiceRepository;
    private final SqlDataServiceModelReferenceRepository sqlServiceModelRepository;
    private final SearchEngine searchEngine;

    public DataSourceRelationQueryService(
            DataSourceRepository dataSourceRepository,
            DataModelRepository modelRepository,
            DataTaskRepository taskRepository,
            LocalSqlTaskDefinitionRepository localDefinitionRepository,
            LocalSqlTaskInputRepository localInputRepository,
            CanvasTaskDefinitionRepository canvasDefinitionRepository,
            TaskCanvasModelReferenceRepository canvasModelReferenceRepository,
            TaskDataSourceReferenceRepository dataSourceReferenceRepository,
            SparkJarTaskDefinitionRepository sparkJarDefinitionRepository,
            SparkJarTaskResourceBindingRepository sparkJarBindingRepository,
            CanvasTaskDefinitionService canvasDefinitionService,
            ApiResourceRepository apiResourceRepository,
            SpatialFeatureResourceRepository spatialResourceRepository,
            DataServiceRepository serviceRepository,
            StandardDataServiceDefinitionRepository standardServiceRepository,
            SqlDataServiceDefinitionRepository sqlServiceRepository,
            ScriptDataServiceDefinitionRepository scriptServiceRepository,
            SqlDataServiceModelReferenceRepository sqlServiceModelRepository,
            SearchEngine searchEngine
    ) {
        this.dataSourceRepository = dataSourceRepository;
        this.modelRepository = modelRepository;
        this.taskRepository = taskRepository;
        this.localDefinitionRepository = localDefinitionRepository;
        this.localInputRepository = localInputRepository;
        this.canvasDefinitionRepository = canvasDefinitionRepository;
        this.canvasModelReferenceRepository = canvasModelReferenceRepository;
        this.dataSourceReferenceRepository = dataSourceReferenceRepository;
        this.sparkJarDefinitionRepository = sparkJarDefinitionRepository;
        this.sparkJarBindingRepository = sparkJarBindingRepository;
        this.canvasDefinitionService = canvasDefinitionService;
        this.apiResourceRepository = apiResourceRepository;
        this.spatialResourceRepository = spatialResourceRepository;
        this.serviceRepository = serviceRepository;
        this.standardServiceRepository = standardServiceRepository;
        this.sqlServiceRepository = sqlServiceRepository;
        this.scriptServiceRepository = scriptServiceRepository;
        this.sqlServiceModelRepository = sqlServiceModelRepository;
        this.searchEngine = searchEngine;
    }

    @Transactional(readOnly = true)
    public PageResponse<DataSourceRelatedModelResponse> relatedModels(UUID dataSourceId, SearchRequest request) {
        requireDataSource(dataSourceId);
        Specification<DataModel> fixed = (root, query, builder) ->
                builder.equal(root.get("storageDataSourceId"), dataSourceId);
        Page<DataModel> page = searchEngine.search(
                withDefaultSort(request), DataModel.class, modelRepository, fixed
        );
        return pageResponse(page, page.getContent().stream().map(DataSourceRelatedModelResponse::from).toList());
    }

    @Transactional(readOnly = true)
    public PageResponse<DataSourceRelatedTaskResponse> relatedTasks(
            UUID dataSourceId,
            DataSourceTaskRelationRole role,
            DataSourceRelationKind relationKind,
            SearchRequest request
    ) {
        requireDataSource(dataSourceId);
        Page<DataTask> page = searchEngine.search(
                withDefaultSort(request), DataTask.class, taskRepository,
                relatedTaskSpecification(dataSourceId, role, relationKind)
        );
        List<UUID> taskIds = page.getContent().stream().map(DataTask::getId).toList();
        if (taskIds.isEmpty()) {
            return pageResponse(page, List.of());
        }

        Map<UUID, LocalSqlTaskDefinition> localDefinitions = localDefinitionRepository.findAllByTaskIdIn(taskIds).stream()
                .collect(Collectors.toMap(LocalSqlTaskDefinition::getTaskId, Function.identity()));
        Map<UUID, CanvasTaskDefinition> canvasDefinitions = canvasDefinitionRepository.findAllByTaskIdIn(taskIds).stream()
                .collect(Collectors.toMap(CanvasTaskDefinition::getTaskId, Function.identity()));
        Map<UUID, SparkJarTaskDefinition> sparkJarDefinitions = sparkJarDefinitionRepository
                .findAllByTaskIdIn(taskIds).stream()
                .collect(Collectors.toMap(SparkJarTaskDefinition::getTaskId, Function.identity()));
        Map<UUID, Map<UUID, CanvasNodeDefinition>> nodesByTask = canvasDefinitions.values().stream()
                .collect(Collectors.toMap(CanvasTaskDefinition::getTaskId, this::canvasNodes));
        List<LocalSqlTaskInput> localInputs = localInputRepository
                .findAllByTaskIdInOrderByTaskIdAscSortOrderAsc(taskIds);
        List<TaskCanvasModelReference> canvasModelReferences = canvasModelReferenceRepository
                .findAllByTaskIdInOrderByTaskIdAscNodeIdAsc(taskIds);
        List<SparkJarTaskResourceBinding> sparkJarBindings = sparkJarBindingRepository
                .findAllByTaskIdInOrderByTaskIdAscCreatedAtAsc(taskIds);
        Set<UUID> modelIds = new LinkedHashSet<>();
        localInputs.forEach(input -> modelIds.add(input.getModelId()));
        localDefinitions.values().forEach(definition -> modelIds.add(definition.getOutputModelId()));
        canvasModelReferences.forEach(reference -> modelIds.add(reference.getModelId()));
        sparkJarBindings.stream()
                .filter(binding -> binding.getResourceType() == SparkJarResourceType.MODEL)
                .forEach(binding -> modelIds.add(binding.getResourceId()));
        modelIds.remove(null);
        Map<UUID, DataModel> models = modelRepository.findAllById(modelIds).stream()
                .filter(model -> dataSourceId.equals(model.getStorageDataSourceId()))
                .collect(Collectors.toMap(DataModel::getId, Function.identity()));
        List<TaskDataSourceReference> directReferences = dataSourceReferenceRepository
                .findAllByTaskIdInAndDataSourceIdOrderByTaskIdAscLocationKeyAsc(taskIds, dataSourceId);
        Set<UUID> apiResourceIds = nodesByTask.values().stream()
                .flatMap(nodes -> nodes.values().stream())
                .map(DataSourceRelationQueryService::apiResourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Set<UUID> spatialResourceIds = nodesByTask.values().stream()
                .flatMap(nodes -> nodes.values().stream())
                .map(DataSourceRelationQueryService::spatialResourceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> apiResourceNames = apiResourceRepository.findAllById(apiResourceIds).stream()
                .filter(resource -> dataSourceId.equals(resource.getDataSourceId()))
                .collect(Collectors.toMap(ApiResource::getId, ApiResource::getName));
        Map<UUID, String> spatialResourceNames = spatialResourceRepository.findAllById(spatialResourceIds).stream()
                .filter(resource -> dataSourceId.equals(resource.getDataSourceId()))
                .collect(Collectors.toMap(SpatialFeatureResource::getId, SpatialFeatureResource::getName));
        Map<UUID, List<DataSourceTaskReferenceLocationResponse>> locations = new HashMap<>();

        directReferences.stream()
                .filter(item -> matches(role, item.getReferenceRole()) && matches(relationKind, DataSourceRelationKind.DIRECT))
                .forEach(item -> addLocation(locations, item.getTaskId(), directLocation(
                        item,
                        nodesByTask.getOrDefault(item.getTaskId(), Map.of()).get(uuidOrNull(item.getLocationKey())),
                        apiResourceNames,
                        spatialResourceNames
                )));
        sparkJarBindings.stream()
                .filter(binding -> binding.getResourceType() != SparkJarResourceType.MODEL)
                .filter(binding -> dataSourceId.equals(binding.getResourceId()))
                .forEach(binding -> addSparkJarJdbcLocations(locations, binding, role, relationKind));

        if (relationKind == null || relationKind == DataSourceRelationKind.VIA_MODEL) {
            localInputs.stream()
                    .filter(input -> models.containsKey(input.getModelId()))
                    .filter(input -> role == null || role == DataSourceTaskRelationRole.INPUT)
                    .forEach(input -> addLocation(
                            locations, input.getTaskId(), modelLocation(
                                    DataSourceTaskRelationRole.INPUT,
                                    "local-input:" + input.getSortOrder(),
                                    "输入 #" + (input.getSortOrder() + 1),
                                    models.get(input.getModelId())
                            )
                    ));
            localDefinitions.values().stream()
                    .filter(item -> models.containsKey(item.getOutputModelId()))
                    .filter(item -> role == null || role == DataSourceTaskRelationRole.OUTPUT)
                    .forEach(item -> addLocation(
                            locations, item.getTaskId(), modelLocation(
                                    DataSourceTaskRelationRole.OUTPUT,
                                    "local-output",
                                    "输出模型",
                                    models.get(item.getOutputModelId())
                            )
                    ));
            canvasModelReferences.stream()
                    .filter(item -> models.containsKey(item.getModelId()))
                    .filter(item -> matches(role, item.getReferenceRole()))
                    .forEach(item -> {
                        CanvasNodeDefinition node = nodesByTask.getOrDefault(item.getTaskId(), Map.of()).get(item.getNodeId());
                        addLocation(locations, item.getTaskId(), modelLocation(
                                item.getReferenceRole() == TaskCanvasModelReferenceRole.INPUT
                                        ? DataSourceTaskRelationRole.INPUT : DataSourceTaskRelationRole.OUTPUT,
                                item.getNodeId().toString(),
                                node == null ? item.getNodeId().toString() : node.name(),
                                models.get(item.getModelId())
                        ));
                    });
            sparkJarBindings.stream()
                    .filter(binding -> binding.getResourceType() == SparkJarResourceType.MODEL)
                    .filter(binding -> models.containsKey(binding.getResourceId()))
                    .forEach(binding -> addSparkJarModelLocations(locations, binding, models.get(binding.getResourceId()), role));
        }

        List<DataSourceRelatedTaskResponse> content = page.getContent().stream().map(task -> {
            List<DataSourceTaskReferenceLocationResponse> taskLocations = locations
                    .getOrDefault(task.getId(), List.of()).stream()
                    .sorted(Comparator.comparing(DataSourceTaskReferenceLocationResponse::role)
                            .thenComparing(DataSourceTaskReferenceLocationResponse::relationKind)
                            .thenComparing(DataSourceTaskReferenceLocationResponse::locationLabel))
                    .toList();
            return new DataSourceRelatedTaskResponse(
                    task.getId(), task.getName(), task.getType(), task.getStatus(),
                    definitionVersion(task, localDefinitions, canvasDefinitions, sparkJarDefinitions),
                    distinct(taskLocations.stream().map(DataSourceTaskReferenceLocationResponse::role).toList()),
                    distinct(taskLocations.stream().map(DataSourceTaskReferenceLocationResponse::relationKind).toList()),
                    taskLocations, task.getUpdatedAt()
            );
        }).toList();
        return pageResponse(page, content);
    }

    @Transactional(readOnly = true)
    public PageResponse<DataSourceRelatedServiceResponse> relatedServices(
            UUID dataSourceId,
            DataSourceRelationKind relationKind,
            SearchRequest request
    ) {
        requireDataSource(dataSourceId);
        List<UUID> modelIds = modelRepository.findAllByStorageDataSourceId(dataSourceId).stream()
                .map(DataModel::getId).toList();
        Map<UUID, EnumSet<DataSourceRelationKind>> relations = new HashMap<>();
        if (relationKind == null || relationKind == DataSourceRelationKind.DIRECT) {
            sqlServiceRepository.findAllByDataSourceId(dataSourceId)
                    .forEach(item -> addRelation(relations, item.getDataServiceId(), DataSourceRelationKind.DIRECT));
            scriptServiceRepository.findAllByDataSourceId(dataSourceId)
                    .forEach(item -> addRelation(relations, item.getDataServiceId(), DataSourceRelationKind.DIRECT));
        }
        if ((relationKind == null || relationKind == DataSourceRelationKind.VIA_MODEL) && !modelIds.isEmpty()) {
            standardServiceRepository.findAllByModelIdIn(modelIds)
                    .forEach(item -> addRelation(relations, item.getDataServiceId(), DataSourceRelationKind.VIA_MODEL));
            sqlServiceModelRepository.findAllByModelIdIn(modelIds)
                    .forEach(item -> addRelation(relations, item.getDataServiceId(), DataSourceRelationKind.VIA_MODEL));
        }
        Set<UUID> serviceIds = relations.keySet();
        Specification<DataService> fixed = (root, query, builder) -> serviceIds.isEmpty()
                ? builder.disjunction() : root.get("id").in(serviceIds);
        Page<DataService> page = searchEngine.search(
                withDefaultSort(request), DataService.class, serviceRepository, fixed
        );
        List<UUID> pageIds = page.getContent().stream().map(DataService::getId).toList();
        Map<UUID, StandardDataServiceDefinition> standards = standardServiceRepository
                .findAllByDataServiceIdIn(pageIds).stream()
                .collect(Collectors.toMap(StandardDataServiceDefinition::getDataServiceId, Function.identity()));
        Map<UUID, SqlDataServiceDefinition> sqlDefinitions = sqlServiceRepository
                .findAllByDataServiceIdIn(pageIds).stream()
                .collect(Collectors.toMap(SqlDataServiceDefinition::getDataServiceId, Function.identity()));
        Map<UUID, ScriptDataServiceDefinition> scripts = scriptServiceRepository
                .findAllByDataServiceIdIn(pageIds).stream()
                .collect(Collectors.toMap(ScriptDataServiceDefinition::getDataServiceId, Function.identity()));
        List<DataSourceRelatedServiceResponse> content = page.getContent().stream()
                .map(service -> new DataSourceRelatedServiceResponse(
                        service.getId(), service.getCode(), service.getName(), service.getType(), service.getStatus(),
                        serviceDefinitionVersion(service.getId(), standards, sqlDefinitions, scripts),
                        List.copyOf(relations.getOrDefault(service.getId(), EnumSet.noneOf(DataSourceRelationKind.class))),
                        service.getRoutePath(), service.getUpdatedAt()
                ))
                .toList();
        return pageResponse(page, content);
    }

    private Specification<DataTask> relatedTaskSpecification(
            UUID dataSourceId,
            DataSourceTaskRelationRole role,
            DataSourceRelationKind relationKind
    ) {
        return (root, query, builder) -> {
            List<Predicate> alternatives = new ArrayList<>();
            if (relationKind == null || relationKind == DataSourceRelationKind.DIRECT) {
                if (role == null || role == DataSourceTaskRelationRole.INPUT) {
                    alternatives.add(sparkJarJdbcBindingExists(query, builder, root, dataSourceId,
                            SparkJarResourceAccessMode.READ, SparkJarResourceAccessMode.READ_WRITE));
                }
                if (role == null || role == DataSourceTaskRelationRole.OUTPUT) {
                    alternatives.add(sparkJarJdbcBindingExists(query, builder, root, dataSourceId,
                            SparkJarResourceAccessMode.WRITE, SparkJarResourceAccessMode.READ_WRITE));
                }
                Subquery<Integer> directQuery = query.subquery(Integer.class);
                Root<TaskDataSourceReference> direct = directQuery.from(TaskDataSourceReference.class);
                List<Predicate> predicates = new ArrayList<>(List.of(
                        builder.equal(direct.get("taskId"), root.get("id")),
                        builder.equal(direct.get("dataSourceId"), dataSourceId)
                ));
                if (role != null) {
                    predicates.add(builder.equal(
                            direct.get("referenceRole"),
                            role == DataSourceTaskRelationRole.INPUT
                                    ? TaskDataSourceReferenceRole.INPUT : TaskDataSourceReferenceRole.OUTPUT
                    ));
                }
                directQuery.select(builder.literal(1)).where(predicates.toArray(Predicate[]::new));
                alternatives.add(builder.exists(directQuery));
            }
            if (relationKind == null || relationKind == DataSourceRelationKind.VIA_MODEL) {
                if (role == null || role == DataSourceTaskRelationRole.INPUT) {
                    alternatives.add(sparkJarModelBindingExists(query, builder, root, dataSourceId,
                            SparkJarResourceAccessMode.READ, SparkJarResourceAccessMode.READ_WRITE));
                    Subquery<Integer> localInputQuery = query.subquery(Integer.class);
                    Root<LocalSqlTaskInput> input = localInputQuery.from(LocalSqlTaskInput.class);
                    Root<DataModel> model = localInputQuery.from(DataModel.class);
                    localInputQuery.select(builder.literal(1)).where(
                            builder.equal(input.get("taskId"), root.get("id")),
                            builder.equal(input.get("modelId"), model.get("id")),
                            builder.equal(model.get("storageDataSourceId"), dataSourceId)
                    );
                    alternatives.add(builder.exists(localInputQuery));
                    alternatives.add(canvasModelExists(query, builder, root, dataSourceId, TaskCanvasModelReferenceRole.INPUT));
                }
                if (role == null || role == DataSourceTaskRelationRole.OUTPUT) {
                    alternatives.add(sparkJarModelBindingExists(query, builder, root, dataSourceId,
                            SparkJarResourceAccessMode.WRITE, SparkJarResourceAccessMode.READ_WRITE));
                    Subquery<Integer> localOutputQuery = query.subquery(Integer.class);
                    Root<LocalSqlTaskDefinition> output = localOutputQuery.from(LocalSqlTaskDefinition.class);
                    Root<DataModel> model = localOutputQuery.from(DataModel.class);
                    localOutputQuery.select(builder.literal(1)).where(
                            builder.equal(output.get("taskId"), root.get("id")),
                            builder.equal(output.get("outputModelId"), model.get("id")),
                            builder.equal(model.get("storageDataSourceId"), dataSourceId)
                    );
                    alternatives.add(builder.exists(localOutputQuery));
                    alternatives.add(canvasModelExists(query, builder, root, dataSourceId, TaskCanvasModelReferenceRole.OUTPUT));
                }
            }
            return alternatives.isEmpty() ? builder.disjunction() : builder.or(alternatives.toArray(Predicate[]::new));
        };
    }

    private Predicate sparkJarJdbcBindingExists(
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            Root<DataTask> task,
            UUID dataSourceId,
            SparkJarResourceAccessMode first,
            SparkJarResourceAccessMode second
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<SparkJarTaskResourceBinding> binding = subquery.from(SparkJarTaskResourceBinding.class);
        subquery.select(builder.literal(1)).where(
                builder.equal(binding.get("taskId"), task.get("id")),
                binding.get("resourceType").in(
                        SparkJarResourceType.JDBC_DATA_SOURCE, SparkJarResourceType.KAFKA_TOPIC),
                builder.equal(binding.get("resourceId"), dataSourceId),
                binding.get("accessMode").in(first, second)
        );
        return builder.exists(subquery);
    }

    private Predicate sparkJarModelBindingExists(
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            Root<DataTask> task,
            UUID dataSourceId,
            SparkJarResourceAccessMode first,
            SparkJarResourceAccessMode second
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<SparkJarTaskResourceBinding> binding = subquery.from(SparkJarTaskResourceBinding.class);
        Root<DataModel> model = subquery.from(DataModel.class);
        subquery.select(builder.literal(1)).where(
                builder.equal(binding.get("taskId"), task.get("id")),
                builder.equal(binding.get("resourceType"), SparkJarResourceType.MODEL),
                builder.equal(binding.get("resourceId"), model.get("id")),
                builder.equal(model.get("storageDataSourceId"), dataSourceId),
                binding.get("accessMode").in(first, second)
        );
        return builder.exists(subquery);
    }

    private Predicate canvasModelExists(
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder builder,
            Root<DataTask> task,
            UUID dataSourceId,
            TaskCanvasModelReferenceRole role
    ) {
        Subquery<Integer> subquery = query.subquery(Integer.class);
        Root<TaskCanvasModelReference> reference = subquery.from(TaskCanvasModelReference.class);
        Root<DataModel> model = subquery.from(DataModel.class);
        subquery.select(builder.literal(1)).where(
                builder.equal(reference.get("taskId"), task.get("id")),
                builder.equal(reference.get("modelId"), model.get("id")),
                builder.equal(reference.get("referenceRole"), role),
                builder.equal(model.get("storageDataSourceId"), dataSourceId)
        );
        return builder.exists(subquery);
    }

    private Map<UUID, CanvasNodeDefinition> canvasNodes(CanvasTaskDefinition definition) {
        return canvasDefinitionService.readIfCompatible(definition)
                .map(canvas -> canvas.nodes().stream().collect(Collectors.toMap(
                        node -> UUID.fromString(node.id()), Function.identity())))
                .orElseGet(Map::of);
    }

    private DataSourceTaskReferenceLocationResponse directLocation(
            TaskDataSourceReference reference,
            CanvasNodeDefinition node,
            Map<UUID, String> apiResourceNames,
            Map<UUID, String> spatialResourceNames
    ) {
        return new DataSourceTaskReferenceLocationResponse(
                reference.getReferenceRole() == TaskDataSourceReferenceRole.INPUT
                        ? DataSourceTaskRelationRole.INPUT : DataSourceTaskRelationRole.OUTPUT,
                DataSourceRelationKind.DIRECT,
                DataSourceTaskResourceKind.valueOf(reference.getResourceKind().name()),
                reference.getLocationKey(),
                node == null ? reference.getLocationKey() : node.name(),
                directResourceLabel(node, apiResourceNames, spatialResourceNames),
                null,
                null
        );
    }

    private String directResourceLabel(
            CanvasNodeDefinition node,
            Map<UUID, String> apiResourceNames,
            Map<UUID, String> spatialResourceNames
    ) {
        if (node instanceof JdbcInputNodeDefinition input) return input.configuration().tableName();
        if (node instanceof JdbcQueryInputNodeDefinition input) return input.configuration().outputTableName();
        if (node instanceof HttpApiInputNodeDefinition input) {
            return resourceName(input.configuration().resourceId(), apiResourceNames);
        }
        if (node instanceof SpatialServiceInputNodeDefinition input) {
            return resourceName(input.configuration().resourceId(), spatialResourceNames);
        }
        if (node instanceof KafkaInputNodeDefinition input) return input.configuration().topic();
        if (node instanceof TdEngineTmqInputNodeDefinition input) {
            return input.configuration().topicName() + " · " + input.configuration().supertableName();
        }
        if (node instanceof JdbcOutputNodeDefinition output) return output.configuration().targetTableName();
        if (node instanceof JdbcSnapshotSyncOutputNodeDefinition output) return output.configuration().targetTableName();
        if (node instanceof KafkaOutputNodeDefinition output) return output.configuration().topic();
        if (node instanceof FileOutputNodeDefinition output) return output.configuration().targetPath();
        return null;
    }

    private static String resourceName(String id, Map<UUID, String> names) {
        UUID resourceId = uuidOrNull(id);
        return resourceId == null ? id : names.getOrDefault(resourceId, id);
    }

    private static UUID apiResourceId(CanvasNodeDefinition node) {
        return node instanceof HttpApiInputNodeDefinition input
                ? uuidOrNull(input.configuration().resourceId()) : null;
    }

    private static UUID spatialResourceId(CanvasNodeDefinition node) {
        return node instanceof SpatialServiceInputNodeDefinition input
                ? uuidOrNull(input.configuration().resourceId()) : null;
    }

    private static DataSourceTaskReferenceLocationResponse modelLocation(
            DataSourceTaskRelationRole role,
            String locationKey,
            String locationLabel,
            DataModel model
    ) {
        return new DataSourceTaskReferenceLocationResponse(
                role, DataSourceRelationKind.VIA_MODEL, DataSourceTaskResourceKind.MODEL,
                locationKey, locationLabel, model.getName(), model.getId(), model.getName()
        );
    }

    private static void addSparkJarJdbcLocations(
            Map<UUID, List<DataSourceTaskReferenceLocationResponse>> locations,
            SparkJarTaskResourceBinding binding,
            DataSourceTaskRelationRole role,
            DataSourceRelationKind relationKind
    ) {
        if (relationKind != null && relationKind != DataSourceRelationKind.DIRECT) return;
        boolean kafka = binding.getResourceType() == SparkJarResourceType.KAFKA_TOPIC;
        DataSourceTaskResourceKind kind = kafka
                ? DataSourceTaskResourceKind.KAFKA_TOPIC : DataSourceTaskResourceKind.JDBC_DATA_SOURCE;
        String locationKey = kafka ? binding.getTopicName() : binding.getBindingName();
        String label = kafka ? "Spark JAR Kafka Topic 绑定" : "Spark JAR JDBC 绑定";
        if (binding.getAccessMode().canRead()
                && (role == null || role == DataSourceTaskRelationRole.INPUT)) {
            addLocation(locations, binding.getTaskId(), new DataSourceTaskReferenceLocationResponse(
                    DataSourceTaskRelationRole.INPUT, DataSourceRelationKind.DIRECT,
                    kind, locationKey, binding.getBindingName(), label, null, null));
        }
        if (binding.getAccessMode().canWrite()
                && (role == null || role == DataSourceTaskRelationRole.OUTPUT)) {
            addLocation(locations, binding.getTaskId(), new DataSourceTaskReferenceLocationResponse(
                    DataSourceTaskRelationRole.OUTPUT, DataSourceRelationKind.DIRECT,
                    kind, locationKey, binding.getBindingName(), label, null, null));
        }
    }

    private static void addSparkJarModelLocations(
            Map<UUID, List<DataSourceTaskReferenceLocationResponse>> locations,
            SparkJarTaskResourceBinding binding,
            DataModel model,
            DataSourceTaskRelationRole role
    ) {
        if (binding.getAccessMode().canRead()
                && (role == null || role == DataSourceTaskRelationRole.INPUT)) {
            addLocation(locations, binding.getTaskId(), modelLocation(
                    DataSourceTaskRelationRole.INPUT, "jar-binding:" + binding.getBindingName(),
                    binding.getBindingName(), model));
        }
        if (binding.getAccessMode().canWrite()
                && (role == null || role == DataSourceTaskRelationRole.OUTPUT)) {
            addLocation(locations, binding.getTaskId(), modelLocation(
                    DataSourceTaskRelationRole.OUTPUT, "jar-binding:" + binding.getBindingName(),
                    binding.getBindingName(), model));
        }
    }

    private static int definitionVersion(
            DataTask task,
            Map<UUID, LocalSqlTaskDefinition> localDefinitions,
            Map<UUID, CanvasTaskDefinition> canvasDefinitions,
            Map<UUID, SparkJarTaskDefinition> sparkJarDefinitions
    ) {
        if (task.getType().isCanvas()) {
            CanvasTaskDefinition definition = canvasDefinitions.get(task.getId());
            return definition == null ? 0 : definition.getVersion();
        }
        if (task.getType().isJar()) {
            SparkJarTaskDefinition definition = sparkJarDefinitions.get(task.getId());
            return definition == null ? 0 : definition.getVersion();
        }
        LocalSqlTaskDefinition definition = localDefinitions.get(task.getId());
        return definition == null ? 0 : definition.getVersion();
    }

    private static Integer serviceDefinitionVersion(
            UUID serviceId,
            Map<UUID, StandardDataServiceDefinition> standards,
            Map<UUID, SqlDataServiceDefinition> sqlDefinitions,
            Map<UUID, ScriptDataServiceDefinition> scripts
    ) {
        if (standards.containsKey(serviceId)) return standards.get(serviceId).getVersion();
        if (sqlDefinitions.containsKey(serviceId)) return sqlDefinitions.get(serviceId).getVersion();
        if (scripts.containsKey(serviceId)) return scripts.get(serviceId).getVersion();
        return null;
    }

    private void requireDataSource(UUID dataSourceId) {
        if (!dataSourceRepository.existsById(dataSourceId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "数据源不存在");
        }
    }

    private static SearchRequest withDefaultSort(SearchRequest request) {
        SearchRequest effective = request == null ? SearchRequest.empty() : request;
        if (effective.sort() != null && !effective.sort().isBlank()) return effective;
        return new SearchRequest(effective.search(), effective.page(), effective.size(), "-updatedAt,name");
    }

    private static boolean matches(DataSourceTaskRelationRole expected, TaskDataSourceReferenceRole actual) {
        return expected == null || expected.name().equals(actual.name());
    }

    private static boolean matches(DataSourceTaskRelationRole expected, TaskCanvasModelReferenceRole actual) {
        return expected == null || expected.name().equals(actual.name());
    }

    private static boolean matches(DataSourceRelationKind expected, DataSourceRelationKind actual) {
        return expected == null || expected == actual;
    }

    private static UUID uuidOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static void addLocation(
            Map<UUID, List<DataSourceTaskReferenceLocationResponse>> locations,
            UUID taskId,
            DataSourceTaskReferenceLocationResponse location
    ) {
        locations.computeIfAbsent(taskId, ignored -> new ArrayList<>()).add(location);
    }

    private static void addRelation(
            Map<UUID, EnumSet<DataSourceRelationKind>> relations,
            UUID serviceId,
            DataSourceRelationKind relation
    ) {
        relations.computeIfAbsent(serviceId, ignored -> EnumSet.noneOf(DataSourceRelationKind.class)).add(relation);
    }

    private static <T extends Enum<T>> List<T> distinct(List<T> values) {
        if (values.isEmpty()) return List.of();
        EnumSet<T> result = EnumSet.noneOf(values.getFirst().getDeclaringClass());
        result.addAll(values);
        return List.copyOf(result);
    }

    private static <T, R> PageResponse<R> pageResponse(Page<T> page, List<R> content) {
        return new PageResponse<>(
                content, page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }
}

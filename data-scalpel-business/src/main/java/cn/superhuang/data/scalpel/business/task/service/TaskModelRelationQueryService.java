package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.canvas.CanvasDefinition;
import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.LocalSqlTaskInput;
import cn.superhuang.data.scalpel.business.task.domain.TaskCanvasModelReference;
import cn.superhuang.data.scalpel.business.task.domain.TaskCanvasModelReferenceRole;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskInputRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskCanvasModelReferenceRepository;
import cn.superhuang.data.scalpel.business.task.web.response.ModelRelatedTaskResponse;
import cn.superhuang.data.scalpel.business.task.web.response.ModelTaskReferenceType;
import cn.superhuang.data.scalpel.business.task.web.response.ModelTaskRelationRole;
import cn.superhuang.data.scalpel.business.task.web.response.TaskModelReferenceLocationResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskModelRelationsResponse;
import cn.superhuang.data.scalpel.business.task.web.response.TaskRelatedModelResponse;
import cn.superhuang.data.scalpel.contract.page.PageResponse;
import cn.superhuang.data.scalpel.contract.search.SearchRequest;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TaskModelRelationQueryService {

    private static final Comparator<TaskModelReferenceLocationResponse> LOCATION_ORDER = Comparator
            .comparing(TaskModelReferenceLocationResponse::role)
            .thenComparing(TaskModelReferenceLocationResponse::referenceType)
            .thenComparing(
                    TaskModelReferenceLocationResponse::ordinal,
                    Comparator.nullsLast(Integer::compareTo)
            )
            .thenComparing(
                    TaskModelReferenceLocationResponse::nodeName,
                    Comparator.nullsLast(String::compareTo)
            )
            .thenComparing(
                    TaskModelReferenceLocationResponse::nodeId,
                    Comparator.nullsLast(UUID::compareTo)
            );

    private final DataTaskRepository taskRepository;
    private final LocalSqlTaskDefinitionRepository localSqlDefinitionRepository;
    private final LocalSqlTaskInputRepository localSqlInputRepository;
    private final CanvasTaskDefinitionRepository canvasDefinitionRepository;
    private final TaskCanvasModelReferenceRepository canvasReferenceRepository;
    private final DataModelRepository modelRepository;
    private final CanvasTaskDefinitionService canvasDefinitionService;
    private final SearchEngine searchEngine;

    public TaskModelRelationQueryService(
            DataTaskRepository taskRepository,
            LocalSqlTaskDefinitionRepository localSqlDefinitionRepository,
            LocalSqlTaskInputRepository localSqlInputRepository,
            CanvasTaskDefinitionRepository canvasDefinitionRepository,
            TaskCanvasModelReferenceRepository canvasReferenceRepository,
            DataModelRepository modelRepository,
            CanvasTaskDefinitionService canvasDefinitionService,
            SearchEngine searchEngine
    ) {
        this.taskRepository = taskRepository;
        this.localSqlDefinitionRepository = localSqlDefinitionRepository;
        this.localSqlInputRepository = localSqlInputRepository;
        this.canvasDefinitionRepository = canvasDefinitionRepository;
        this.canvasReferenceRepository = canvasReferenceRepository;
        this.modelRepository = modelRepository;
        this.canvasDefinitionService = canvasDefinitionService;
        this.searchEngine = searchEngine;
    }

    @Transactional(readOnly = true)
    public PageResponse<ModelRelatedTaskResponse> searchRelatedTasks(
            UUID modelId,
            ModelTaskRelationRole role,
            SearchRequest request
    ) {
        if (!modelRepository.existsById(modelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在");
        }
        SearchRequest effectiveRequest = withDefaultSort(request);
        Page<DataTask> page = searchEngine.search(
                effectiveRequest,
                DataTask.class,
                taskRepository,
                relatedTaskSpecification(modelId, role)
        );
        List<UUID> taskIds = page.getContent().stream().map(DataTask::getId).toList();
        if (taskIds.isEmpty()) {
            return new PageResponse<>(
                    List.of(), page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
            );
        }

        Map<UUID, LocalSqlTaskDefinition> localDefinitions = localSqlDefinitionRepository
                .findAllByTaskIdIn(taskIds).stream()
                .collect(Collectors.toMap(LocalSqlTaskDefinition::getTaskId, Function.identity()));
        Map<UUID, CanvasTaskDefinition> canvasDefinitions = canvasDefinitionRepository
                .findAllByTaskIdIn(taskIds).stream()
                .collect(Collectors.toMap(CanvasTaskDefinition::getTaskId, Function.identity()));
        Map<UUID, Map<UUID, String>> canvasNodeNames = canvasNodeNames(canvasDefinitions.values());
        Map<UUID, List<TaskModelReferenceLocationResponse>> locationsByTask = new HashMap<>();

        localSqlInputRepository.findAllByTaskIdInAndModelIdOrderByTaskIdAscSortOrderAsc(taskIds, modelId)
                .forEach(input -> addLocation(
                        locationsByTask,
                        input.getTaskId(),
                        localSqlInputLocation(input)
                ));
        localDefinitions.values().stream()
                .filter(definition -> definition.getOutputModelId().equals(modelId))
                .forEach(definition -> addLocation(
                        locationsByTask,
                        definition.getTaskId(),
                        localSqlOutputLocation()
                ));
        canvasReferenceRepository.findAllByTaskIdInAndModelIdOrderByTaskIdAscNodeIdAsc(taskIds, modelId)
                .forEach(reference -> addLocation(
                        locationsByTask,
                        reference.getTaskId(),
                        canvasLocation(reference, canvasNodeNames.getOrDefault(reference.getTaskId(), Map.of()))
                ));

        List<ModelRelatedTaskResponse> content = page.getContent().stream()
                .map(task -> {
                    List<TaskModelReferenceLocationResponse> locations = sortedLocations(
                            locationsByTask.getOrDefault(task.getId(), List.of())
                    );
                    int definitionVersion = task.getType().isCanvas()
                            ? canvasDefinitions.get(task.getId()).getVersion()
                            : localDefinitions.get(task.getId()).getVersion();
                    return new ModelRelatedTaskResponse(
                            task.getId(),
                            task.getName(),
                            task.getType(),
                            task.getStatus(),
                            definitionVersion,
                            roles(locations),
                            locations,
                            task.getUpdatedAt()
                    );
                })
                .toList();
        return new PageResponse<>(
                content, page.getTotalElements(), page.getTotalPages(), page.getNumber(), page.getSize()
        );
    }

    @Transactional(readOnly = true)
    public TaskModelRelationsResponse getTaskModelRelations(UUID taskId) {
        DataTask task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
        Map<UUID, List<TaskModelReferenceLocationResponse>> locationsByModel = new LinkedHashMap<>();
        Integer definitionVersion;
        if (task.getType().isCanvas()) {
            CanvasTaskDefinition definition = canvasDefinitionRepository.findByTaskId(taskId).orElse(null);
            if (definition == null) {
                return new TaskModelRelationsResponse(taskId, false, null, List.of());
            }
            Map<UUID, String> nodeNames = nodeNames(definition);
            canvasReferenceRepository.findAllByTaskIdOrderByNodeId(taskId).forEach(reference -> addLocation(
                    locationsByModel,
                    reference.getModelId(),
                    canvasLocation(reference, nodeNames)
            ));
            definitionVersion = definition.getVersion();
        } else {
            LocalSqlTaskDefinition definition = localSqlDefinitionRepository.findByTaskId(taskId).orElse(null);
            if (definition == null) {
                return new TaskModelRelationsResponse(taskId, false, null, List.of());
            }
            localSqlInputRepository.findAllByTaskIdOrderBySortOrderAsc(taskId).forEach(input -> addLocation(
                    locationsByModel,
                    input.getModelId(),
                    localSqlInputLocation(input)
            ));
            addLocation(locationsByModel, definition.getOutputModelId(), localSqlOutputLocation());
            definitionVersion = definition.getVersion();
        }

        Map<UUID, DataModel> models = requireModels(locationsByModel.keySet());
        List<TaskRelatedModelResponse> relatedModels = locationsByModel.entrySet().stream()
                .map(entry -> {
                    DataModel model = models.get(entry.getKey());
                    List<TaskModelReferenceLocationResponse> locations = sortedLocations(entry.getValue());
                    return new TaskRelatedModelResponse(
                            model.getId(),
                            model.getCode(),
                            model.getName(),
                            model.getStatus(),
                            model.getPhysicalTableMode(),
                            model.getSchemaVersion(),
                            roles(locations),
                            locations
                    );
                })
                .sorted(Comparator
                        .comparingInt((TaskRelatedModelResponse item) -> item.roles().getFirst().ordinal())
                        .thenComparing(TaskRelatedModelResponse::modelCode))
                .toList();
        return new TaskModelRelationsResponse(taskId, true, definitionVersion, relatedModels);
    }

    private Specification<DataTask> relatedTaskSpecification(UUID modelId, ModelTaskRelationRole role) {
        return (root, query, builder) -> {
            List<Predicate> alternatives = new ArrayList<>();
            if (role == null || role == ModelTaskRelationRole.INPUT) {
                Subquery<Integer> localInputQuery = query.subquery(Integer.class);
                Root<LocalSqlTaskInput> localInput = localInputQuery.from(LocalSqlTaskInput.class);
                localInputQuery.select(builder.literal(1)).where(
                        builder.equal(localInput.get("taskId"), root.get("id")),
                        builder.equal(localInput.get("modelId"), modelId)
                );
                alternatives.add(builder.exists(localInputQuery));

                Subquery<Integer> canvasInputQuery = query.subquery(Integer.class);
                Root<TaskCanvasModelReference> canvasInput = canvasInputQuery.from(TaskCanvasModelReference.class);
                canvasInputQuery.select(builder.literal(1)).where(
                        builder.equal(canvasInput.get("taskId"), root.get("id")),
                        builder.equal(canvasInput.get("modelId"), modelId),
                        builder.equal(
                                canvasInput.get("referenceRole"),
                                TaskCanvasModelReferenceRole.INPUT
                        )
                );
                alternatives.add(builder.exists(canvasInputQuery));
            }
            if (role == null || role == ModelTaskRelationRole.OUTPUT) {
                Subquery<Integer> localOutputQuery = query.subquery(Integer.class);
                Root<LocalSqlTaskDefinition> localOutput = localOutputQuery.from(LocalSqlTaskDefinition.class);
                localOutputQuery.select(builder.literal(1)).where(
                        builder.equal(localOutput.get("taskId"), root.get("id")),
                        builder.equal(localOutput.get("outputModelId"), modelId)
                );
                alternatives.add(builder.exists(localOutputQuery));

                Subquery<Integer> canvasOutputQuery = query.subquery(Integer.class);
                Root<TaskCanvasModelReference> canvasOutput = canvasOutputQuery.from(TaskCanvasModelReference.class);
                canvasOutputQuery.select(builder.literal(1)).where(
                        builder.equal(canvasOutput.get("taskId"), root.get("id")),
                        builder.equal(canvasOutput.get("modelId"), modelId),
                        builder.equal(
                                canvasOutput.get("referenceRole"),
                                TaskCanvasModelReferenceRole.OUTPUT
                        )
                );
                alternatives.add(builder.exists(canvasOutputQuery));
            }
            return builder.or(alternatives.toArray(Predicate[]::new));
        };
    }

    private static SearchRequest withDefaultSort(SearchRequest request) {
        SearchRequest effective = request == null ? SearchRequest.empty() : request;
        if (effective.sort() != null && !effective.sort().isBlank()) {
            return effective;
        }
        return new SearchRequest(effective.search(), effective.page(), effective.size(), "-updatedAt,name");
    }

    private Map<UUID, DataModel> requireModels(Collection<UUID> modelIds) {
        Map<UUID, DataModel> models = modelRepository.findAllById(modelIds).stream()
                .collect(Collectors.toMap(DataModel::getId, Function.identity()));
        if (models.size() != modelIds.size()) {
            throw new IllegalStateException("任务引用的模型不存在");
        }
        return models;
    }

    private Map<UUID, Map<UUID, String>> canvasNodeNames(Collection<CanvasTaskDefinition> definitions) {
        return definitions.stream().collect(Collectors.toMap(
                CanvasTaskDefinition::getTaskId,
                this::nodeNames
        ));
    }

    private Map<UUID, String> nodeNames(CanvasTaskDefinition definition) {
        return canvasDefinitionService.deserialize(definition.getDefinitionJson()).nodes().stream()
                .collect(Collectors.toMap(
                        node -> UUID.fromString(node.id()),
                        CanvasDefinition.CanvasNodeDefinition::name
                ));
    }

    private static TaskModelReferenceLocationResponse localSqlInputLocation(LocalSqlTaskInput input) {
        return new TaskModelReferenceLocationResponse(
                ModelTaskRelationRole.INPUT,
                ModelTaskReferenceType.LOCAL_SQL_INPUT,
                input.getSortOrder() + 1,
                null,
                null
        );
    }

    private static TaskModelReferenceLocationResponse localSqlOutputLocation() {
        return new TaskModelReferenceLocationResponse(
                ModelTaskRelationRole.OUTPUT,
                ModelTaskReferenceType.LOCAL_SQL_OUTPUT,
                null,
                null,
                null
        );
    }

    private static TaskModelReferenceLocationResponse canvasLocation(
            TaskCanvasModelReference reference,
            Map<UUID, String> nodeNames
    ) {
        return new TaskModelReferenceLocationResponse(
                reference.getReferenceRole() == TaskCanvasModelReferenceRole.INPUT
                        ? ModelTaskRelationRole.INPUT
                        : ModelTaskRelationRole.OUTPUT,
                ModelTaskReferenceType.CANVAS_NODE,
                null,
                reference.getNodeId(),
                nodeNames.get(reference.getNodeId())
        );
    }

    private static List<TaskModelReferenceLocationResponse> sortedLocations(
            Collection<TaskModelReferenceLocationResponse> locations
    ) {
        return locations.stream().sorted(LOCATION_ORDER).toList();
    }

    private static List<ModelTaskRelationRole> roles(
            Collection<TaskModelReferenceLocationResponse> locations
    ) {
        EnumSet<ModelTaskRelationRole> roles = EnumSet.noneOf(ModelTaskRelationRole.class);
        locations.forEach(location -> roles.add(location.role()));
        return List.copyOf(roles);
    }

    private static <K> void addLocation(
            Map<K, List<TaskModelReferenceLocationResponse>> locations,
            K key,
            TaskModelReferenceLocationResponse location
    ) {
        locations.computeIfAbsent(key, ignored -> new ArrayList<>()).add(location);
    }
}

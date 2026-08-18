package cn.superhuang.data.scalpel.business.model.service;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageAssetRole;
import cn.superhuang.data.scalpel.business.lineage.domain.TaskLineageAsset;
import cn.superhuang.data.scalpel.business.lineage.repository.TaskLineageAssetRepository;
import cn.superhuang.data.scalpel.business.lineage.repository.TaskLineageSnapshotRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelReferenceServiceResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelReferenceTaskResponse;
import cn.superhuang.data.scalpel.business.model.web.response.DataModelReferencesResponse;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.business.service.repository.SqlDataServiceModelReferenceRepository;
import cn.superhuang.data.scalpel.business.service.repository.StandardDataServiceDefinitionRepository;
import cn.superhuang.data.scalpel.business.service.web.response.DataServiceRelatedModelRole;
import cn.superhuang.data.scalpel.business.task.domain.CanvasTaskDefinition;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.domain.TaskCanvasModelReference;
import cn.superhuang.data.scalpel.business.task.domain.TaskCanvasModelReferenceRole;
import cn.superhuang.data.scalpel.business.task.repository.CanvasTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.LocalSqlTaskInputRepository;
import cn.superhuang.data.scalpel.business.task.repository.ModelQualityTaskDefinitionRepository;
import cn.superhuang.data.scalpel.business.task.repository.SparkJarTaskResourceBindingRepository;
import cn.superhuang.data.scalpel.business.task.repository.TaskCanvasModelReferenceRepository;
import cn.superhuang.data.scalpel.business.task.service.CanvasTaskDefinitionService;
import cn.superhuang.data.scalpel.business.task.web.response.ModelTaskReferenceType;
import cn.superhuang.data.scalpel.business.task.web.response.ModelTaskRelationRole;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.execution.SparkJarResourceType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DataModelReferenceQueryService {

    private final DataModelRepository modelRepository;
    private final DataTaskRepository taskRepository;
    private final LocalSqlTaskDefinitionRepository localDefinitionRepository;
    private final LocalSqlTaskInputRepository localInputRepository;
    private final TaskCanvasModelReferenceRepository canvasReferenceRepository;
    private final CanvasTaskDefinitionRepository canvasDefinitionRepository;
    private final ModelQualityTaskDefinitionRepository qualityDefinitionRepository;
    private final SparkJarTaskResourceBindingRepository sparkJarBindingRepository;
    private final TaskLineageAssetRepository lineageAssetRepository;
    private final TaskLineageSnapshotRepository lineageSnapshotRepository;
    private final DataServiceRepository dataServiceRepository;
    private final StandardDataServiceDefinitionRepository standardServiceRepository;
    private final SqlDataServiceModelReferenceRepository sqlServiceRepository;
    private final CanvasTaskDefinitionService canvasDefinitionService;

    public DataModelReferenceQueryService(
            DataModelRepository modelRepository,
            DataTaskRepository taskRepository,
            LocalSqlTaskDefinitionRepository localDefinitionRepository,
            LocalSqlTaskInputRepository localInputRepository,
            TaskCanvasModelReferenceRepository canvasReferenceRepository,
            CanvasTaskDefinitionRepository canvasDefinitionRepository,
            ModelQualityTaskDefinitionRepository qualityDefinitionRepository,
            SparkJarTaskResourceBindingRepository sparkJarBindingRepository,
            TaskLineageAssetRepository lineageAssetRepository,
            TaskLineageSnapshotRepository lineageSnapshotRepository,
            DataServiceRepository dataServiceRepository,
            StandardDataServiceDefinitionRepository standardServiceRepository,
            SqlDataServiceModelReferenceRepository sqlServiceRepository,
            CanvasTaskDefinitionService canvasDefinitionService
    ) {
        this.modelRepository = modelRepository;
        this.taskRepository = taskRepository;
        this.localDefinitionRepository = localDefinitionRepository;
        this.localInputRepository = localInputRepository;
        this.canvasReferenceRepository = canvasReferenceRepository;
        this.canvasDefinitionRepository = canvasDefinitionRepository;
        this.qualityDefinitionRepository = qualityDefinitionRepository;
        this.sparkJarBindingRepository = sparkJarBindingRepository;
        this.lineageAssetRepository = lineageAssetRepository;
        this.lineageSnapshotRepository = lineageSnapshotRepository;
        this.dataServiceRepository = dataServiceRepository;
        this.standardServiceRepository = standardServiceRepository;
        this.sqlServiceRepository = sqlServiceRepository;
        this.canvasDefinitionService = canvasDefinitionService;
    }

    @Transactional(readOnly = true)
    public DataModelReferencesResponse get(UUID modelId) {
        if (!modelRepository.existsById(modelId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在");
        }
        return references(modelId);
    }

    /** Must be called inside the model deletion transaction to close the preflight race window. */
    public DataModelReferencesResponse authoritative(UUID modelId) {
        return references(modelId);
    }

    private DataModelReferencesResponse references(UUID modelId) {
        List<PendingTaskReference> taskReferences = new ArrayList<>();
        localInputRepository.findAllByModelIdOrderByTaskIdAscSortOrderAsc(modelId).forEach(input ->
                taskReferences.add(new PendingTaskReference(
                        input.getTaskId(), ModelTaskRelationRole.INPUT,
                        ModelTaskReferenceType.LOCAL_SQL_INPUT, null, null)));
        localDefinitionRepository.findAllByOutputModelId(modelId).forEach(definition ->
                taskReferences.add(new PendingTaskReference(
                        definition.getTaskId(), ModelTaskRelationRole.OUTPUT,
                        ModelTaskReferenceType.LOCAL_SQL_OUTPUT, null, null)));

        List<TaskCanvasModelReference> canvasReferences =
                canvasReferenceRepository.findAllByModelIdOrderByTaskIdAscNodeIdAsc(modelId);
        Map<UUID, Map<UUID, String>> canvasNames = canvasNodeNames(
                canvasReferences.stream().map(TaskCanvasModelReference::getTaskId).distinct().toList());
        canvasReferences.forEach(reference -> taskReferences.add(new PendingTaskReference(
                reference.getTaskId(),
                reference.getReferenceRole() == TaskCanvasModelReferenceRole.INPUT
                        ? ModelTaskRelationRole.INPUT : ModelTaskRelationRole.OUTPUT,
                ModelTaskReferenceType.CANVAS_NODE,
                reference.getNodeId(),
                canvasNames.getOrDefault(reference.getTaskId(), Map.of()).get(reference.getNodeId()))));

        qualityDefinitionRepository.findAllByModelId(modelId).forEach(definition ->
                taskReferences.add(new PendingTaskReference(
                        definition.getTaskId(), ModelTaskRelationRole.INPUT,
                        ModelTaskReferenceType.MODEL_QUALITY_TARGET, null, null)));

        sparkJarBindingRepository.findAllByResourceTypeAndResourceIdOrderByTaskIdAscBindingNameAsc(
                SparkJarResourceType.MODEL, modelId).forEach(binding -> {
            if (binding.getAccessMode().canRead()) {
                taskReferences.add(new PendingTaskReference(
                        binding.getTaskId(), ModelTaskRelationRole.INPUT,
                        ModelTaskReferenceType.SPARK_JAR_RESOURCE_BINDING, null,
                        binding.getBindingName()));
            }
            if (binding.getAccessMode().canWrite()) {
                taskReferences.add(new PendingTaskReference(
                        binding.getTaskId(), ModelTaskRelationRole.OUTPUT,
                        ModelTaskReferenceType.SPARK_JAR_RESOURCE_BINDING, null,
                        binding.getBindingName()));
            }
        });

        List<TaskLineageAsset> lineageAssets = lineageAssetRepository.findCurrentByModelIdIn(List.of(modelId));
        Map<UUID, UUID> lineageTaskIds = lineageSnapshotRepository.findAllByIdIn(
                        lineageAssets.stream().map(TaskLineageAsset::getSnapshotId).distinct().toList())
                .stream().collect(Collectors.toMap(snapshot -> snapshot.getId(), snapshot -> snapshot.getTaskId()));
        lineageAssets.forEach(asset -> {
            UUID taskId = lineageTaskIds.get(asset.getSnapshotId());
            if (taskId != null) {
                taskReferences.add(new PendingTaskReference(
                        taskId,
                        asset.getRole() == LineageAssetRole.INPUT
                                ? ModelTaskRelationRole.INPUT : ModelTaskRelationRole.OUTPUT,
                        ModelTaskReferenceType.CURRENT_LINEAGE, null, asset.getFlowKey()));
            }
        });

        Map<UUID, DataTask> tasksById = taskRepository.findAllById(
                        taskReferences.stream().map(PendingTaskReference::taskId).distinct().toList())
                .stream().collect(Collectors.toMap(DataTask::getId, Function.identity()));
        Map<String, DataModelReferenceTaskResponse> tasks = new LinkedHashMap<>();
        taskReferences.forEach(reference -> {
            DataTask task = tasksById.get(reference.taskId());
            if (task == null) return;
            String key = task.getId() + ":" + reference.role() + ":" + reference.referenceType()
                    + ":" + reference.nodeId() + ":" + reference.nodeName();
            tasks.putIfAbsent(key, new DataModelReferenceTaskResponse(
                    task.getId(), task.getName(), task.getType(), task.getStatus(), reference.role(),
                    reference.referenceType(), reference.nodeId(), reference.nodeName()));
        });

        List<PendingServiceReference> serviceReferences = new ArrayList<>();
        standardServiceRepository.findAllByModelId(modelId).forEach(definition ->
                serviceReferences.add(new PendingServiceReference(
                        definition.getDataServiceId(), DataServiceRelatedModelRole.PRIMARY, null)));
        sqlServiceRepository.findAllByModelIdOrderByDataServiceIdAscSortOrderAsc(modelId).forEach(reference ->
                serviceReferences.add(new PendingServiceReference(
                        reference.getDataServiceId(), DataServiceRelatedModelRole.REFERENCE,
                        reference.getSortOrder() + 1)));
        Map<UUID, DataService> servicesById = dataServiceRepository.findAllById(
                        serviceReferences.stream().map(PendingServiceReference::serviceId).distinct().toList())
                .stream().collect(Collectors.toMap(DataService::getId, Function.identity()));
        List<DataModelReferenceServiceResponse> services = serviceReferences.stream()
                .map(reference -> {
                    DataService service = servicesById.get(reference.serviceId());
                    return service == null ? null : new DataModelReferenceServiceResponse(
                            service.getId(), service.getName(), service.getType(), service.getStatus(),
                            reference.role(), reference.ordinal());
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted(Comparator.comparing(DataModelReferenceServiceResponse::name))
                .toList();
        List<DataModelReferenceTaskResponse> taskResponses = tasks.values().stream()
                .sorted(Comparator.comparing(DataModelReferenceTaskResponse::name)
                        .thenComparing(DataModelReferenceTaskResponse::referenceType))
                .toList();
        return new DataModelReferencesResponse(
                modelId, taskResponses.isEmpty() && services.isEmpty(), taskResponses, services);
    }

    private Map<UUID, Map<UUID, String>> canvasNodeNames(List<UUID> taskIds) {
        return canvasDefinitionRepository.findAllByTaskIdIn(taskIds).stream().collect(Collectors.toMap(
                CanvasTaskDefinition::getTaskId,
                definition -> canvasDefinitionService.readIfCompatible(definition)
                        .map(canvas -> canvas.nodes().stream().collect(Collectors.toMap(
                                node -> UUID.fromString(node.id()),
                                CanvasNodeDefinition::name,
                                (left, right) -> left)))
                        .orElseGet(Map::of)
        ));
    }

    private record PendingTaskReference(
            UUID taskId,
            ModelTaskRelationRole role,
            ModelTaskReferenceType referenceType,
            UUID nodeId,
            String nodeName
    ) {
    }

    private record PendingServiceReference(
            UUID serviceId,
            DataServiceRelatedModelRole role,
            Integer ordinal
    ) {
    }
}

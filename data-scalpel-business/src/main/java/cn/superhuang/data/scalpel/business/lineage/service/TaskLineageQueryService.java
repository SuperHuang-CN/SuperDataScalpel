package cn.superhuang.data.scalpel.business.lineage.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.lineage.domain.*;
import cn.superhuang.data.scalpel.business.lineage.repository.*;
import cn.superhuang.data.scalpel.business.lineage.web.response.*;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds one current task snapshot graph without consulting Spark runtime evidence. */
@Service
public class TaskLineageQueryService {
    private final DataTaskRepository taskRepository;
    private final TaskLineageSnapshotRepository snapshotRepository;
    private final TaskLineageAssetRepository assetRepository;
    private final TaskLineageAssetFieldRepository fieldRepository;
    private final TaskLineageFieldEdgeRepository edgeRepository;
    private final TaskLineageFieldUsageRepository usageRepository;
    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository modelFieldRepository;
    private final DataSourceRepository dataSourceRepository;

    public TaskLineageQueryService(
            DataTaskRepository taskRepository,
            TaskLineageSnapshotRepository snapshotRepository,
            TaskLineageAssetRepository assetRepository,
            TaskLineageAssetFieldRepository fieldRepository,
            TaskLineageFieldEdgeRepository edgeRepository,
            TaskLineageFieldUsageRepository usageRepository,
            DataModelRepository modelRepository,
            DataModelFieldRepository modelFieldRepository,
            DataSourceRepository dataSourceRepository
    ) {
        this.taskRepository = taskRepository;
        this.snapshotRepository = snapshotRepository;
        this.assetRepository = assetRepository;
        this.fieldRepository = fieldRepository;
        this.edgeRepository = edgeRepository;
        this.usageRepository = usageRepository;
        this.modelRepository = modelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.dataSourceRepository = dataSourceRepository;
    }

    @Transactional(readOnly = true)
    public TaskLineageGraphResponse table(UUID taskId, String requestedFlowKey) {
        DataTask task = requireTask(taskId);
        Bundle bundle = load(taskId);
        if (bundle.snapshot() == null) return empty(task);
        List<TaskLineageFlowResponse> flows = flowResponses(bundle);
        TaskLineageFlowResponse selected = selectFlow(flows, requestedFlowKey);
        List<TaskLineageAsset> flowAssets = bundle.assets().stream()
                .filter(asset -> asset.getFlowKey().equals(selected.flowKey())).toList();
        TaskLineageAsset output = output(flowAssets);
        String taskNodeId = taskNodeId(bundle.snapshot(), selected.flowKey());
        List<LineageGraphNodeResponse> nodes = new ArrayList<>();
        List<LineageGraphEdgeResponse> edges = new ArrayList<>();
        nodes.add(taskNode(task, bundle.snapshot(), taskNodeId, output.getWriteMode()));
        for (TaskLineageAsset asset : flowAssets) {
            LineageGraphNodeSide side = asset.getRole() == LineageAssetRole.INPUT
                    ? LineageGraphNodeSide.UPSTREAM : LineageGraphNodeSide.DOWNSTREAM;
            LineageGraphNodeResponse node = assetNode(asset, side, bundle);
            nodes.add(node);
            boolean input = asset.getRole() == LineageAssetRole.INPUT;
            edges.add(new LineageGraphEdgeResponse(
                    edgeId(input ? node.id() : taskNodeId, input ? taskNodeId : node.id(), input ? "reads" : "writes"),
                    input ? node.id() : taskNodeId,
                    input ? taskNodeId : node.id(),
                    input ? LineageGraphEdgeType.READS : LineageGraphEdgeType.WRITES,
                    null, null, List.of()
            ));
        }
        return response(taskId, bundle, flows, selected.flowKey(), null,
                new LineageGraphResponse(taskNodeId, LineageGranularity.TABLE,
                        selected.coverage(), false, warnings(bundle, flowAssets), nodes, edges));
    }

    @Transactional(readOnly = true)
    public TaskLineageGraphResponse fields(
            UUID taskId,
            String requestedFlowKey,
            String requestedOutputFieldKey
    ) {
        DataTask task = requireTask(taskId);
        Bundle bundle = load(taskId);
        if (bundle.snapshot() == null) return empty(task);
        List<TaskLineageFlowResponse> flows = flowResponses(bundle);
        TaskLineageFlowResponse selected = selectFlow(flows, requestedFlowKey);
        if (selected.outputFields().isEmpty()) {
            return response(taskId, bundle, flows, selected.flowKey(), null,
                    new LineageGraphResponse(taskNodeId(bundle.snapshot(), selected.flowKey()),
                            LineageGranularity.FIELD, selected.coverage(), false,
                            List.of("当前输出链路只有表级血缘"), List.of(), List.of()));
        }
        TaskLineageOutputFieldResponse selectedField = requestedOutputFieldKey == null
                ? selected.outputFields().getFirst()
                : selected.outputFields().stream()
                .filter(field -> field.fieldKey().equals(requestedOutputFieldKey)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "输出字段不属于所选血缘链路"));
        List<TaskLineageAsset> flowAssets = bundle.assets().stream()
                .filter(asset -> asset.getFlowKey().equals(selected.flowKey())).toList();
        TaskLineageAsset outputAsset = output(flowAssets);
        TaskLineageAssetField target = bundle.fields().stream()
                .filter(field -> field.getAssetId().equals(outputAsset.getId())
                        && field.getFieldKey().equals(selectedField.fieldKey())).findFirst().orElseThrow();

        String taskNodeId = taskNodeId(bundle.snapshot(), selected.flowKey());
        List<LineageGraphNodeResponse> nodes = new ArrayList<>();
        List<LineageGraphEdgeResponse> graphEdges = new ArrayList<>();
        nodes.add(taskNode(task, bundle.snapshot(), taskNodeId, outputAsset.getWriteMode()));
        String targetNodeId = fieldNodeId(target, LineageGraphNodeSide.DOWNSTREAM);
        nodes.add(fieldNode(target, outputAsset, LineageGraphNodeSide.DOWNSTREAM, bundle));

        Map<UUID, TaskLineageAssetField> fieldsById = bundle.fields().stream()
                .collect(Collectors.toMap(TaskLineageAssetField::getId, Function.identity()));
        Map<UUID, TaskLineageAsset> assetsById = bundle.assets().stream()
                .collect(Collectors.toMap(TaskLineageAsset::getId, Function.identity()));

        List<TaskLineageFieldEdge> incoming = bundle.edges().stream()
                .filter(edge -> edge.getFlowKey().equals(selected.flowKey())
                        && edge.getTargetAssetFieldId().equals(target.getId())).toList();
        if (incoming.isEmpty()) {
            graphEdges.add(new LineageGraphEdgeResponse(
                    edgeId(taskNodeId, targetNodeId, "effect"), taskNodeId, targetNodeId,
                    LineageGraphEdgeType.FIELD_EFFECT, null, target.getOutputEffect(), List.of()));
        } else {
            for (TaskLineageFieldEdge edge : incoming) {
                TaskLineageAssetField source = fieldsById.get(edge.getSourceAssetFieldId());
                TaskLineageAsset sourceAsset = source == null ? null : assetsById.get(source.getAssetId());
                if (source == null || sourceAsset == null) continue;
                String sourceNodeId = fieldNodeId(source, LineageGraphNodeSide.UPSTREAM);
                nodes.add(fieldNode(source, sourceAsset, LineageGraphNodeSide.UPSTREAM, bundle));
                graphEdges.add(new LineageGraphEdgeResponse(
                        edgeId(sourceNodeId, taskNodeId, edge.getId().toString()),
                        sourceNodeId, taskNodeId, LineageGraphEdgeType.DERIVES,
                        edge.getDerivationType(), null, List.of()));
            }
            graphEdges.add(new LineageGraphEdgeResponse(
                    edgeId(taskNodeId, targetNodeId, target.getFieldKey()), taskNodeId, targetNodeId,
                    LineageGraphEdgeType.DERIVES, incoming.getFirst().getDerivationType(),
                    target.getOutputEffect(), List.of()));
        }

        bundle.usages().stream()
                .filter(usage -> usage.getFlowKey().equals(selected.flowKey()))
                .collect(Collectors.groupingBy(
                        TaskLineageFieldUsage::getAssetFieldId,
                        LinkedHashMap::new,
                        Collectors.mapping(TaskLineageFieldUsage::getUsageType, Collectors.toSet())
                ))
                .forEach((fieldId, usageTypes) -> {
                    TaskLineageAssetField source = fieldsById.get(fieldId);
                    TaskLineageAsset sourceAsset = source == null ? null : assetsById.get(source.getAssetId());
                    if (source == null || sourceAsset == null || sourceAsset.getRole() != LineageAssetRole.INPUT) {
                        return;
                    }
                    String sourceNodeId = fieldNodeId(source, LineageGraphNodeSide.UPSTREAM);
                    nodes.add(fieldNode(source, sourceAsset, LineageGraphNodeSide.UPSTREAM, bundle));
                    graphEdges.add(new LineageGraphEdgeResponse(
                            edgeId(sourceNodeId, taskNodeId, "usage:" + source.getId()),
                            sourceNodeId, taskNodeId, LineageGraphEdgeType.FIELD_EFFECT,
                            null, null, usageTypes.stream().sorted().toList()));
                });
        return response(taskId, bundle, flows, selected.flowKey(), selectedField.fieldKey(),
                new LineageGraphResponse(taskNodeId, LineageGranularity.FIELD,
                        selected.coverage(), false, warnings(bundle, flowAssets),
                        deduplicateNodes(nodes), graphEdges));
    }

    private Bundle load(UUID taskId) {
        TaskLineageSnapshot snapshot = snapshotRepository
                .findFirstByTaskIdAndRetiredAtIsNullOrderByGenerationDesc(taskId).orElse(null);
        if (snapshot == null) return Bundle.empty();
        Set<UUID> snapshotIds = Set.of(snapshot.getId());
        List<TaskLineageAsset> assets = assetRepository.findAllBySnapshotIdIn(snapshotIds);
        List<TaskLineageAssetField> fields = fieldRepository.findAllBySnapshotIdIn(snapshotIds);
        Set<UUID> modelIds = assets.stream().map(TaskLineageAsset::getModelId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, DataModel> models = modelRepository.findAllById(modelIds).stream()
                .collect(Collectors.toMap(DataModel::getId, Function.identity()));
        Set<UUID> modelFieldIds = fields.stream().map(TaskLineageAssetField::getModelFieldId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, DataModelField> modelFields = modelFieldRepository.findAllById(modelFieldIds).stream()
                .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        Set<UUID> dataSourceIds = assets.stream().map(TaskLineageAsset::getDataSourceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        models.values().stream().map(DataModel::getStorageDataSourceId).forEach(dataSourceIds::add);
        Map<UUID, DataSource> dataSources = dataSourceRepository.findAllById(dataSourceIds).stream()
                .collect(Collectors.toMap(DataSource::getId, Function.identity()));
        return new Bundle(snapshot, assets, fields,
                edgeRepository.findAllBySnapshotIdIn(snapshotIds),
                usageRepository.findAllBySnapshotIdIn(snapshotIds), models, modelFields, dataSources);
    }

    private static List<TaskLineageFlowResponse> flowResponses(Bundle bundle) {
        Map<UUID, List<TaskLineageAssetField>> fieldsByAsset = bundle.fields().stream()
                .collect(Collectors.groupingBy(TaskLineageAssetField::getAssetId));
        return bundle.assets().stream().filter(asset -> asset.getRole() == LineageAssetRole.OUTPUT)
                .sorted(Comparator.comparing(TaskLineageAsset::getFlowKey))
                .map(output -> new TaskLineageFlowResponse(
                        output.getFlowKey(), assetLabel(output, bundle), output.getAssetKind(),
                        output.getWriteMode(), flowCoverage(output, bundle.snapshot()),
                        fieldsByAsset.getOrDefault(output.getId(), List.of()).stream()
                                .sorted(Comparator.comparingInt(TaskLineageAssetField::getSortOrder))
                                .map(field -> new TaskLineageOutputFieldResponse(
                                        field.getFieldKey(), field.getModelFieldId(),
                                        field.getFieldCodeSnapshot(), field.getFieldNameSnapshot(),
                                        field.getSortOrder(), field.getOutputEffect())).toList()
                )).toList();
    }

    private static TaskLineageFlowResponse selectFlow(
            List<TaskLineageFlowResponse> flows, String requestedFlowKey
    ) {
        if (flows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务当前血缘没有输出链路");
        if (requestedFlowKey == null || requestedFlowKey.isBlank()) return flows.getFirst();
        return flows.stream().filter(flow -> flow.flowKey().equals(requestedFlowKey)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "血缘输出链路不存在"));
    }

    private static TaskLineageAsset output(List<TaskLineageAsset> assets) {
        return assets.stream().filter(asset -> asset.getRole() == LineageAssetRole.OUTPUT)
                .findFirst().orElseThrow();
    }

    private static LineageGraphNodeResponse assetNode(
            TaskLineageAsset asset, LineageGraphNodeSide side, Bundle bundle
    ) {
        LineageGraphNodeKind kind = switch (asset.getAssetKind()) {
            case MODEL -> LineageGraphNodeKind.MODEL;
            case JDBC_TABLE -> LineageGraphNodeKind.JDBC_TABLE;
            case EXTERNAL_RESOURCE -> LineageGraphNodeKind.EXTERNAL_RESOURCE;
        };
        DataModel model = bundle.models().get(asset.getModelId());
        DataSource source = bundle.dataSources().get(asset.getDataSourceId());
        boolean stale = asset.getAssetKind() == LineageAssetKind.MODEL
                && (model == null || asset.getModelSchemaVersion() == null
                || model.getSchemaVersion() != asset.getModelSchemaVersion());
        return new LineageGraphNodeResponse(
                assetNodeId(asset, side), kind, side, 1,
                assetLabel(asset, bundle), assetSubtitle(asset, model, source),
                asset.getModelId(), null, null, asset.getDataSourceId(), null, null,
                asset.getWriteMode(), stale, asset.getExternalResourceType(), asset.getResourceId());
    }

    private static LineageGraphNodeResponse fieldNode(
            TaskLineageAssetField field,
            TaskLineageAsset asset,
            LineageGraphNodeSide side,
            Bundle bundle
    ) {
        DataModelField current = bundle.modelFields().get(field.getModelFieldId());
        boolean stale = asset.getAssetKind() == LineageAssetKind.MODEL
                && (current == null || bundle.models().get(asset.getModelId()) == null
                || !Objects.equals(asset.getModelSchemaVersion(),
                bundle.models().get(asset.getModelId()).getSchemaVersion()));
        return new LineageGraphNodeResponse(
                fieldNodeId(field, side), LineageGraphNodeKind.FIELD, side, 1,
                current == null ? field.getFieldNameSnapshot() : current.getName(),
                assetLabel(asset, bundle) + " · "
                        + (current == null ? field.getFieldCodeSnapshot() : current.getCode()),
                asset.getModelId(), field.getModelFieldId(), null, asset.getDataSourceId(),
                null, null, asset.getWriteMode(), stale,
                asset.getExternalResourceType(), asset.getResourceId());
    }

    private static LineageGraphNodeResponse taskNode(
            DataTask task, TaskLineageSnapshot snapshot, String id, LineageWriteMode mode
    ) {
        return new LineageGraphNodeResponse(id, LineageGraphNodeKind.TASK,
                LineageGraphNodeSide.CURRENT, 0, task.getName(), task.getType().name(),
                null, null, task.getId(), null, task.getStatus(), snapshot.getDefinitionVersion(),
                mode, false);
    }

    private static String assetLabel(TaskLineageAsset asset, Bundle bundle) {
        DataModel model = bundle.models().get(asset.getModelId());
        return switch (asset.getAssetKind()) {
            case MODEL -> model == null ? asset.getModelNameSnapshot() : model.getName();
            case JDBC_TABLE -> asset.getPhysicalTableName();
            case EXTERNAL_RESOURCE -> asset.getResourceNameSnapshot();
        };
    }

    private static String assetSubtitle(TaskLineageAsset asset, DataModel model, DataSource source) {
        return switch (asset.getAssetKind()) {
            case MODEL -> model == null ? asset.getModelCodeSnapshot() : model.getCode();
            case JDBC_TABLE -> java.util.stream.Stream.of(
                            source == null ? asset.getDataSourceNameSnapshot() : source.getName(),
                            asset.getCatalogName(), asset.getSchemaName(), asset.getPhysicalTableName())
                    .filter(value -> value != null && !value.isBlank()).collect(Collectors.joining("."));
            case EXTERNAL_RESOURCE -> java.util.stream.Stream.of(
                            asset.getExternalResourceType() == null ? null : asset.getExternalResourceType().name(),
                            source == null ? asset.getDataSourceNameSnapshot() : source.getName())
                    .filter(value -> value != null && !value.isBlank()).collect(Collectors.joining(" · "));
        };
    }

    private static List<String> warnings(Bundle bundle, List<TaskLineageAsset> assets) {
        LinkedHashSet<String> warnings = new LinkedHashSet<>();
        if (assets.stream().anyMatch(asset -> asset.getAssetKind() == LineageAssetKind.MODEL
                && (bundle.models().get(asset.getModelId()) == null
                || !Objects.equals(asset.getModelSchemaVersion(),
                bundle.models().get(asset.getModelId()).getSchemaVersion())))) {
            warnings.add("部分血缘基于旧模型结构，已标记为陈旧");
        }
        if (assets.stream().filter(asset -> asset.getRole() == LineageAssetRole.OUTPUT)
                .map(asset -> flowCoverage(asset, bundle.snapshot()))
                .anyMatch(coverage -> coverage == LineageCoverage.FIELD_PARTIAL)) {
            warnings.add("当前输出链路只有部分字段血缘");
        }
        return List.copyOf(warnings);
    }

    private static LineageCoverage flowCoverage(TaskLineageAsset output, TaskLineageSnapshot snapshot) {
        return output.getFlowCoverage() == null ? snapshot.getCoverage() : output.getFlowCoverage();
    }

    private static String assetNodeId(TaskLineageAsset asset, LineageGraphNodeSide side) {
        return "asset:" + asset.getId() + ':' + side;
    }

    private static String fieldNodeId(TaskLineageAssetField field, LineageGraphNodeSide side) {
        return "field:" + field.getId() + ':' + side;
    }

    private static String taskNodeId(TaskLineageSnapshot snapshot, String flowKey) {
        return "task:" + snapshot.getId() + ':' + flowKey + ":CURRENT";
    }

    private static String edgeId(String source, String target, String suffix) {
        return "edge:" + Integer.toUnsignedString(Objects.hash(source, target, suffix), 36);
    }

    private static List<LineageGraphNodeResponse> deduplicateNodes(List<LineageGraphNodeResponse> nodes) {
        Map<String, LineageGraphNodeResponse> byId = new LinkedHashMap<>();
        nodes.forEach(node -> byId.putIfAbsent(node.id(), node));
        return List.copyOf(byId.values());
    }

    private DataTask requireTask(UUID taskId) {
        return taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    private static TaskLineageGraphResponse response(
            UUID taskId, Bundle bundle, List<TaskLineageFlowResponse> flows,
            String selectedFlowKey, String selectedFieldKey, LineageGraphResponse graph
    ) {
        return new TaskLineageGraphResponse(taskId, bundle.snapshot().getDefinitionVersion(),
                bundle.snapshot().getCoverage(), flows, selectedFlowKey, selectedFieldKey, graph);
    }

    private static TaskLineageGraphResponse empty(DataTask task) {
        return new TaskLineageGraphResponse(task.getId(), null, null, List.of(), null, null,
                new LineageGraphResponse(null, LineageGranularity.TABLE, null,
                        false, List.of(), List.of(), List.of()));
    }

    private record Bundle(
            TaskLineageSnapshot snapshot,
            List<TaskLineageAsset> assets,
            List<TaskLineageAssetField> fields,
            List<TaskLineageFieldEdge> edges,
            List<TaskLineageFieldUsage> usages,
            Map<UUID, DataModel> models,
            Map<UUID, DataModelField> modelFields,
            Map<UUID, DataSource> dataSources
    ) {
        private static Bundle empty() {
            return new Bundle(null, List.of(), List.of(), List.of(), List.of(),
                    Map.of(), Map.of(), Map.of());
        }
    }
}

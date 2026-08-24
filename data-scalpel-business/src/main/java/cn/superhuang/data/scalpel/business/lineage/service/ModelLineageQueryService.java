package cn.superhuang.data.scalpel.business.lineage.service;

import cn.superhuang.data.scalpel.business.datasource.domain.DataSource;
import cn.superhuang.data.scalpel.business.datasource.repository.DataSourceRepository;
import cn.superhuang.data.scalpel.business.lineage.domain.*;
import cn.superhuang.data.scalpel.business.lineage.repository.*;
import cn.superhuang.data.scalpel.business.lineage.web.request.LineageDirection;
import cn.superhuang.data.scalpel.business.lineage.web.response.*;
import cn.superhuang.data.scalpel.business.model.domain.DataModel;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.model.repository.DataModelRepository;
import cn.superhuang.data.scalpel.business.task.domain.DataTask;
import cn.superhuang.data.scalpel.business.task.repository.DataTaskRepository;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Builds bounded model-centered lineage graphs from current published snapshots. */
@Service
public class ModelLineageQueryService {

    private static final int MAXIMUM_NODES = 200;
    private static final int MAXIMUM_EDGES = 600;
    private static final int DEFAULT_FIELD_COUNT = 20;

    private final DataModelRepository modelRepository;
    private final DataModelFieldRepository modelFieldRepository;
    private final DataTaskRepository taskRepository;
    private final DataSourceRepository dataSourceRepository;
    private final TaskLineageSnapshotRepository snapshotRepository;
    private final TaskLineageAssetRepository assetRepository;
    private final TaskLineageAssetFieldRepository assetFieldRepository;
    private final TaskLineageFieldEdgeRepository fieldEdgeRepository;
    private final TaskLineageFieldUsageRepository fieldUsageRepository;
    private final StandardDataServiceLineageService standardServiceLineageService;

    public ModelLineageQueryService(
            DataModelRepository modelRepository,
            DataModelFieldRepository modelFieldRepository,
            DataTaskRepository taskRepository,
            DataSourceRepository dataSourceRepository,
            TaskLineageSnapshotRepository snapshotRepository,
            TaskLineageAssetRepository assetRepository,
            TaskLineageAssetFieldRepository assetFieldRepository,
            TaskLineageFieldEdgeRepository fieldEdgeRepository,
            TaskLineageFieldUsageRepository fieldUsageRepository,
            StandardDataServiceLineageService standardServiceLineageService
    ) {
        this.modelRepository = modelRepository;
        this.modelFieldRepository = modelFieldRepository;
        this.taskRepository = taskRepository;
        this.dataSourceRepository = dataSourceRepository;
        this.snapshotRepository = snapshotRepository;
        this.assetRepository = assetRepository;
        this.assetFieldRepository = assetFieldRepository;
        this.fieldEdgeRepository = fieldEdgeRepository;
        this.fieldUsageRepository = fieldUsageRepository;
        this.standardServiceLineageService = standardServiceLineageService;
    }

    @Transactional(readOnly = true)
    public LineageGraphResponse tableLineage(UUID modelId, LineageDirection direction, int depth) {
        DataModel rootModel = requireModel(modelId);
        LineageDirection effectiveDirection = direction == null ? LineageDirection.BOTH : direction;
        requireDepth(depth);
        GraphBuilder graph = new GraphBuilder(
                modelNodeId(modelId, LineageGraphNodeSide.CURRENT), LineageGranularity.TABLE
        );
        graph.addNode(modelNode(rootModel, LineageGraphNodeSide.CURRENT, 0, false));
        if (effectiveDirection.includesUpstream()) {
            walkTable(modelId, LineageGraphNodeSide.UPSTREAM, depth, graph);
        }
        if (effectiveDirection.includesDownstream() && !graph.truncated()) {
            walkTable(modelId, LineageGraphNodeSide.DOWNSTREAM, depth, graph);
            appendTableServices(graph);
        }
        return graph.response();
    }

    @Transactional(readOnly = true)
    public LineageGraphResponse fieldLineage(
            UUID modelId,
            UUID fieldId,
            LineageDirection direction,
            int depth
    ) {
        return fieldLineages(modelId, List.of(fieldId), direction, depth).graph();
    }

    @Transactional(readOnly = true)
    public LineageFieldGraphResponse fieldLineages(
            UUID modelId,
            List<UUID> requestedFieldIds,
            LineageDirection direction,
            int depth
    ) {
        DataModel rootModel = requireModel(modelId);
        LineageDirection effectiveDirection = direction == null ? LineageDirection.BOTH : direction;
        requireDepth(depth);
        List<DataModelField> selectedFields = selectFields(modelId, requestedFieldIds);
        String rootId = selectedFields.isEmpty()
                ? null : fieldNodeId(selectedFields.getFirst().getId(), LineageGraphNodeSide.CURRENT);
        GraphBuilder graph = new GraphBuilder(rootId, LineageGranularity.FIELD);
        LinkedHashMap<UUID, Set<String>> roots = new LinkedHashMap<>();
        for (DataModelField field : selectedFields) {
            String focusKey = field.getId().toString();
            roots.put(field.getId(), Set.of(focusKey));
            graph.addNode(new LineageGraphNodeResponse(
                    fieldNodeId(field.getId(), LineageGraphNodeSide.CURRENT),
                    LineageGraphNodeKind.FIELD, LineageGraphNodeSide.CURRENT, 0,
                    field.getName(), rootModel.getName() + " · " + field.getCode(),
                    rootModel.getId(), field.getId(), null, null, null, null, null, false,
                    null, null, null, null, null, null,
                    new LineageFieldOwnerResponse(
                            modelNodeId(rootModel.getId(), LineageGraphNodeSide.CURRENT),
                            LineageGraphNodeKind.MODEL, rootModel.getName(), rootModel.getCode(), field.getSortOrder()
                    ), false, List.of()
            ), Set.of(focusKey), true);
        }
        if (effectiveDirection.includesUpstream()) {
            walkFields(roots, LineageGraphNodeSide.UPSTREAM, depth, graph);
        }
        if (effectiveDirection.includesDownstream() && !graph.truncated()) {
            walkFields(roots, LineageGraphNodeSide.DOWNSTREAM, depth, graph);
            appendFieldServices(graph);
        }
        Set<String> unresolved = selectedFields.stream().map(field -> field.getId().toString())
                .filter(key -> graph.focusCoverage(key) == null).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!unresolved.isEmpty()) {
            FieldCoverageFallback fallback = fieldCoverageFallback(modelId, effectiveDirection);
            graph.includeCoverage(unresolved, fallback.coverage());
            if (fallback.warning() != null) graph.warn(fallback.warning());
        }
        List<LineageFocusFieldResponse> focusFields = selectedFields.stream()
                .map(field -> graph.focusField(field, field.getId().toString())).toList();
        return new LineageFieldGraphResponse(graph.response(), focusFields);
    }

    private List<DataModelField> selectFields(UUID modelId, List<UUID> requestedFieldIds) {
        List<DataModelField> ordered = modelFieldRepository.findAllByModelIdOrderBySortOrderAscCodeAsc(modelId);
        if (requestedFieldIds == null) return ordered.stream().limit(DEFAULT_FIELD_COUNT).toList();
        if (requestedFieldIds.isEmpty()) return List.of();
        Set<UUID> requested = new LinkedHashSet<>(requestedFieldIds);
        List<DataModelField> selected = ordered.stream().filter(field -> requested.contains(field.getId())).toList();
        if (selected.size() != requested.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部分模型字段不存在或不属于当前模型");
        }
        return selected;
    }

    private void appendTableServices(GraphBuilder graph) {
        List<LineageGraphNodeResponse> modelNodes = graph.nodes().stream()
                .filter(node -> node.kind() == LineageGraphNodeKind.MODEL)
                .filter(node -> node.side() != LineageGraphNodeSide.UPSTREAM)
                .filter(node -> node.modelId() != null)
                .toList();
        Map<UUID, List<StandardDataServiceLineageService.StandardServiceLineage>> servicesByModel =
                standardServiceLineageService.findEffectiveByModelIds(
                                modelNodes.stream().map(LineageGraphNodeResponse::modelId).toList())
                        .stream().collect(Collectors.groupingBy(item -> item.definition().getModelId()));
        for (LineageGraphNodeResponse modelNode : modelNodes) {
            for (StandardDataServiceLineageService.StandardServiceLineage item
                    : servicesByModel.getOrDefault(modelNode.modelId(), List.of())) {
                LineageGraphNodeResponse serviceNode = dataServiceNode(
                        item, LineageGraphNodeSide.DOWNSTREAM, modelNode.depth()
                );
                graph.addNode(serviceNode);
                graph.addEdge(
                        edgeId(modelNode.id(), serviceNode.id(), "exposes"),
                        modelNode.id(), serviceNode.id(), LineageGraphEdgeType.EXPOSES,
                        null, null, List.of()
                );
                item.warnings().forEach(graph::warn);
            }
        }
    }

    private void appendFieldServices(GraphBuilder graph) {
        List<LineageGraphNodeResponse> fieldNodes = graph.nodes().stream()
                .filter(node -> node.kind() == LineageGraphNodeKind.FIELD)
                .filter(node -> node.side() != LineageGraphNodeSide.UPSTREAM)
                .filter(node -> node.modelId() != null && node.modelFieldId() != null)
                .toList();
        Map<UUID, List<StandardDataServiceLineageService.StandardServiceLineage>> servicesByModel =
                standardServiceLineageService.findEffectiveByModelIds(
                                fieldNodes.stream().map(LineageGraphNodeResponse::modelId).toList())
                        .stream().collect(Collectors.groupingBy(item -> item.definition().getModelId()));
        for (LineageGraphNodeResponse fieldNode : fieldNodes) {
            for (StandardDataServiceLineageService.StandardServiceLineage item
                    : servicesByModel.getOrDefault(fieldNode.modelId(), List.of())) {
                item.warnings().forEach(graph::warn);
                if (!item.exposes(fieldNode.modelFieldId())) continue;
                LineageGraphNodeResponse serviceNode = dataServiceNode(
                        item, LineageGraphNodeSide.DOWNSTREAM, fieldNode.depth()
                );
                Set<String> focusKeys = new LinkedHashSet<>(fieldNode.focusFieldKeys());
                graph.addNode(serviceNode, focusKeys, false);
                graph.addEdge(
                        edgeId(fieldNode.id(), serviceNode.id(), "exposes:" + fieldNode.modelFieldId()),
                        fieldNode.id(), serviceNode.id(), LineageGraphEdgeType.EXPOSES,
                        null, null, List.of(), focusKeys
                );
                graph.includeCoverage(focusKeys, LineageCoverage.FIELD_COMPLETE);
            }
        }
    }

    private void walkFields(
            Map<UUID, Set<String>> roots,
            LineageGraphNodeSide side,
            int maximumDepth,
            GraphBuilder graph
    ) {
        Map<UUID, Set<String>> frontier = copyPaths(roots);
        Map<UUID, Set<String>> visitedPaths = copyPaths(roots);
        for (int level = 1; level <= maximumDepth && !frontier.isEmpty() && !graph.truncated(); level++) {
            List<TaskLineageAssetField> matched = assetFieldRepository.findCurrentByModelFieldIdIn(frontier.keySet());
            if (matched.isEmpty()) break;
            Set<UUID> snapshotIds = matched.stream().map(TaskLineageAssetField::getSnapshotId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            SnapshotBundle metadata = loadBundle(snapshotIds, false);
            Map<UUID, Set<String>> currentFrontier = frontier;
            List<TaskLineageAssetField> anchors = matched.stream()
                    .filter(field -> {
                        TaskLineageAsset asset = metadata.assets().get(field.getAssetId());
                        return asset != null && (side == LineageGraphNodeSide.UPSTREAM
                                ? asset.getRole() == LineageAssetRole.OUTPUT
                                : asset.getRole() == LineageAssetRole.INPUT);
                    })
                    .sorted(Comparator
                            .comparingInt((TaskLineageAssetField field) -> rootOrder(currentFrontier, field.getModelFieldId()))
                            .thenComparing(TaskLineageAssetField::getSortOrder)
                            .thenComparing(TaskLineageAssetField::getId))
                    .toList();
            if (anchors.isEmpty()) break;

            Set<UUID> anchorIds = anchors.stream().map(TaskLineageAssetField::getId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            List<TaskLineageFieldEdge> directionalEdges = side == LineageGraphNodeSide.UPSTREAM
                    ? fieldEdgeRepository.findAllByTargetAssetFieldIdIn(
                            anchorIds, PageRequest.of(0, MAXIMUM_EDGES + 1, Sort.by("id")))
                    : fieldEdgeRepository.findAllBySourceAssetFieldIdIn(
                            anchorIds, PageRequest.of(0, MAXIMUM_EDGES + 1, Sort.by("id")));
            Set<UUID> counterpartIds = directionalEdges.stream()
                    .map(edge -> side == LineageGraphNodeSide.UPSTREAM
                            ? edge.getSourceAssetFieldId() : edge.getTargetAssetFieldId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<UUID, TaskLineageAssetField> fields = new LinkedHashMap<>();
            anchors.forEach(field -> fields.put(field.getId(), field));
            assetFieldRepository.findAllById(counterpartIds).forEach(field -> fields.put(field.getId(), field));
            Set<UUID> neededAssetIds = fields.values().stream().map(TaskLineageAssetField::getAssetId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<UUID, TaskLineageAsset> assets = new LinkedHashMap<>(metadata.assets());
            Set<UUID> missingAssetIds = neededAssetIds.stream().filter(id -> !assets.containsKey(id))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            assetRepository.findAllById(missingAssetIds).forEach(asset -> assets.put(asset.getId(), asset));
            Set<UUID> modelFieldIds = fields.values().stream().map(TaskLineageAssetField::getModelFieldId)
                    .filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new));
            Map<UUID, DataModelField> modelFields = modelFieldRepository.findAllById(modelFieldIds).stream()
                    .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
            List<TaskLineageFieldUsage> usages = fieldUsageRepository.findAllByAssetFieldIdIn(
                    fields.keySet(), PageRequest.of(0, MAXIMUM_EDGES + 1, Sort.by("id")));
            SnapshotBundle bundle = new SnapshotBundle(
                    metadata.snapshots(), assets, metadata.assetsBySnapshotFlow(), fields,
                    directionalEdges, usages, metadata.tasks(), metadata.models(), modelFields,
                    metadata.dataSources()
            );
            Map<UUID, List<TaskLineageFieldEdge>> edgesByAnchor = directionalEdges.stream()
                    .collect(Collectors.groupingBy(edge -> side == LineageGraphNodeSide.UPSTREAM
                            ? edge.getTargetAssetFieldId() : edge.getSourceAssetFieldId()));
            Map<UUID, List<TaskLineageFieldUsage>> usagesByField = usages.stream()
                    .collect(Collectors.groupingBy(TaskLineageFieldUsage::getAssetFieldId));
            Map<UUID, Set<String>> next = new LinkedHashMap<>();

            for (TaskLineageAssetField anchorField : anchors) {
                Set<String> focusKeys = frontier.getOrDefault(anchorField.getModelFieldId(), Set.of());
                if (focusKeys.isEmpty()) continue;
                TaskLineageAsset anchorAsset = assets.get(anchorField.getAssetId());
                TaskLineageSnapshot snapshot = metadata.snapshots().get(anchorField.getSnapshotId());
                if (anchorAsset == null || snapshot == null) continue;
                List<TaskLineageAsset> flowAssets = metadata.assetsBySnapshotFlow().getOrDefault(
                        new SnapshotFlow(snapshot.getId(), anchorAsset.getFlowKey()), List.of());
                LineageCoverage flowCoverage = flowCoverage(anchorAsset, flowAssets, snapshot);
                if (flowCoverage == LineageCoverage.MODEL_ONLY) {
                    graph.includeCoverage(focusKeys, flowCoverage);
                    continue;
                }
                List<TaskLineageFieldEdge> anchorEdges = edgesByAnchor.getOrDefault(anchorField.getId(), List.of());
                List<TaskLineageFieldUsage> anchorUsages = usagesByField.getOrDefault(anchorField.getId(), List.of())
                        .stream().filter(usage -> usage.getFlowKey().equals(anchorAsset.getFlowKey())).toList();
                if (side == LineageGraphNodeSide.DOWNSTREAM && anchorEdges.isEmpty() && anchorUsages.isEmpty()) continue;
                graph.includeCoverage(focusKeys, flowCoverage);
                String taskId = taskNodeId(snapshot, anchorAsset.getFlowKey(), side);
                LineageWriteMode writeMode = flowAssets.stream()
                        .filter(asset -> asset.getRole() == LineageAssetRole.OUTPUT)
                        .map(TaskLineageAsset::getWriteMode).findFirst().orElse(null);
                graph.addNode(taskNode(snapshot, anchorAsset.getFlowKey(), side, level,
                        metadata.tasks().get(snapshot.getTaskId()), writeMode), focusKeys, false);
                LineageGraphNodeSide anchorSide = roots.containsKey(anchorField.getModelFieldId()) && level == 1
                        ? LineageGraphNodeSide.CURRENT : side;
                String anchorNodeId = fieldNodeId(anchorField, anchorSide);
                boolean anchorStale = isStale(anchorAsset, metadata.models(), anchorField, modelFields);
                if (anchorSide == LineageGraphNodeSide.CURRENT) {
                    if (anchorStale) graph.markStale(anchorNodeId);
                    graph.addFocus(anchorNodeId, focusKeys);
                } else {
                    graph.addNode(fieldNode(anchorField, anchorAsset, anchorSide, level, anchorStale,
                            metadata.models(), modelFields), focusKeys, false);
                }
                addUsageEdge(bundle, anchorField, anchorAsset, taskId, side, level, graph, focusKeys);

                if (side == LineageGraphNodeSide.UPSTREAM && anchorEdges.isEmpty()
                        && anchorField.getOutputEffect() != null
                        && anchorField.getOutputEffect() != LineageOutputFieldEffect.DERIVED) {
                    graph.addEdge(edgeId(taskId, anchorNodeId, "effect"), taskId, anchorNodeId,
                            LineageGraphEdgeType.FIELD_EFFECT, null, anchorField.getOutputEffect(), List.of(), focusKeys);
                }
                for (TaskLineageFieldEdge edge : anchorEdges) {
                    TaskLineageAssetField counterpart = fields.get(side == LineageGraphNodeSide.UPSTREAM
                            ? edge.getSourceAssetFieldId() : edge.getTargetAssetFieldId());
                    TaskLineageAsset counterpartAsset = counterpart == null ? null : assets.get(counterpart.getAssetId());
                    if (counterpart == null || counterpartAsset == null) continue;
                    String counterpartNodeId = fieldNodeId(counterpart, side);
                    graph.addNode(fieldNode(counterpart, counterpartAsset, side, level,
                            isStale(counterpartAsset, metadata.models(), counterpart, modelFields),
                            metadata.models(), modelFields), focusKeys, false);
                    if (side == LineageGraphNodeSide.UPSTREAM) {
                        addUsageEdge(bundle, counterpart, counterpartAsset, taskId, side, level, graph, focusKeys);
                        graph.addEdge(edgeId(counterpartNodeId, taskId, edge.getId().toString()), counterpartNodeId, taskId,
                                LineageGraphEdgeType.DERIVES, edge.getDerivationType(), null, List.of(), focusKeys);
                        graph.addEdge(edgeId(taskId, anchorNodeId, edge.getDerivationKey()), taskId, anchorNodeId,
                                LineageGraphEdgeType.DERIVES, edge.getDerivationType(), anchorField.getOutputEffect(),
                                List.of(), focusKeys);
                    } else {
                        graph.addEdge(edgeId(anchorNodeId, taskId, edge.getId().toString()), anchorNodeId, taskId,
                                LineageGraphEdgeType.DERIVES, edge.getDerivationType(), null, List.of(), focusKeys);
                        graph.addEdge(edgeId(taskId, counterpartNodeId, edge.getDerivationKey()), taskId, counterpartNodeId,
                                LineageGraphEdgeType.DERIVES, edge.getDerivationType(), counterpart.getOutputEffect(),
                                List.of(), focusKeys);
                    }
                    if (counterpart.getModelFieldId() != null) {
                        Set<String> unseen = unseenPaths(visitedPaths, counterpart.getModelFieldId(), focusKeys);
                        if (!unseen.isEmpty()) next.computeIfAbsent(counterpart.getModelFieldId(), ignored -> new LinkedHashSet<>())
                                .addAll(unseen);
                    }
                }
            }
            frontier = next;
        }
    }

    private static Map<UUID, Set<String>> copyPaths(Map<UUID, Set<String>> source) {
        Map<UUID, Set<String>> copy = new LinkedHashMap<>();
        source.forEach((key, value) -> copy.put(key, new LinkedHashSet<>(value)));
        return copy;
    }

    private static Set<String> unseenPaths(
            Map<UUID, Set<String>> visitedPaths,
            UUID fieldId,
            Set<String> focusKeys
    ) {
        Set<String> visited = visitedPaths.computeIfAbsent(fieldId, ignored -> new LinkedHashSet<>());
        Set<String> unseen = focusKeys.stream().filter(key -> !visited.contains(key))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        visited.addAll(unseen);
        return unseen;
    }

    private static int rootOrder(Map<UUID, Set<String>> frontier, UUID fieldId) {
        int index = 0;
        for (UUID current : frontier.keySet()) {
            if (current.equals(fieldId)) return index;
            index++;
        }
        return Integer.MAX_VALUE;
    }

    private void walkTable(
            UUID rootModelId,
            LineageGraphNodeSide side,
            int maximumDepth,
            GraphBuilder graph
    ) {
        Set<UUID> frontier = Set.of(rootModelId);
        Set<UUID> visited = new HashSet<>();
        visited.add(rootModelId);
        for (int level = 1; level <= maximumDepth && !frontier.isEmpty() && !graph.truncated(); level++) {
            List<TaskLineageAsset> matched = assetRepository.findCurrentByModelIdIn(frontier).stream()
                    .filter(asset -> side == LineageGraphNodeSide.UPSTREAM
                            ? asset.getRole() == LineageAssetRole.OUTPUT
                            : asset.getRole() == LineageAssetRole.INPUT)
                    .toList();
            if (matched.isEmpty()) break;
            SnapshotBundle bundle = loadBundle(matched.stream().map(TaskLineageAsset::getSnapshotId).collect(Collectors.toSet()), false);
            Set<UUID> next = new LinkedHashSet<>();
            for (TaskLineageAsset anchor : matched) {
                if (graph.truncated()) break;
                TaskLineageSnapshot snapshot = bundle.snapshots().get(anchor.getSnapshotId());
                if (snapshot == null) continue;
                List<TaskLineageAsset> flowAssets = bundle.assetsBySnapshotFlow().getOrDefault(
                        new SnapshotFlow(anchor.getSnapshotId(), anchor.getFlowKey()), List.of()
                );
                List<TaskLineageAsset> related = flowAssets.stream()
                        .filter(asset -> side == LineageGraphNodeSide.UPSTREAM
                                ? asset.getRole() == LineageAssetRole.INPUT
                                : asset.getRole() == LineageAssetRole.OUTPUT)
                        .toList();
                if (related.isEmpty() && side == LineageGraphNodeSide.DOWNSTREAM) continue;
                graph.includeCoverage(flowCoverage(anchor, flowAssets, snapshot));
                String taskNodeId = taskNodeId(snapshot, anchor.getFlowKey(), side);
                LineageWriteMode writeMode = flowAssets.stream()
                        .filter(asset -> asset.getRole() == LineageAssetRole.OUTPUT)
                        .map(TaskLineageAsset::getWriteMode)
                        .findFirst()
                        .orElse(null);
                graph.addNode(taskNode(
                        snapshot, anchor.getFlowKey(), side, level,
                        bundle.tasks().get(snapshot.getTaskId()), writeMode
                ));
                String anchorNodeId = assetNodeId(anchor, side == LineageGraphNodeSide.UPSTREAM
                        ? (anchor.getModelId().equals(rootModelId) && level == 1
                            ? LineageGraphNodeSide.CURRENT : side)
                        : (anchor.getModelId().equals(rootModelId) && level == 1
                            ? LineageGraphNodeSide.CURRENT : side));
                if (isStale(anchor, bundle.models())) {
                    graph.markStale(anchorNodeId);
                }
                if (side == LineageGraphNodeSide.UPSTREAM) {
                    graph.addEdge(edgeId(taskNodeId, anchorNodeId, "writes"), taskNodeId, anchorNodeId,
                            LineageGraphEdgeType.WRITES, null, null, List.of());
                } else {
                    graph.addEdge(edgeId(anchorNodeId, taskNodeId, "reads"), anchorNodeId, taskNodeId,
                            LineageGraphEdgeType.READS, null, null, List.of());
                }
                for (TaskLineageAsset relatedAsset : related) {
                    boolean stale = isStale(relatedAsset, bundle.models());
                    String assetNodeId = assetNodeId(relatedAsset, side);
                    graph.addNode(assetNode(
                            relatedAsset, side, level, stale, bundle.models(), bundle.dataSources()
                    ));
                    if (side == LineageGraphNodeSide.UPSTREAM) {
                        graph.addEdge(edgeId(assetNodeId, taskNodeId, "reads"), assetNodeId, taskNodeId,
                                LineageGraphEdgeType.READS, null, null, List.of());
                    } else {
                        graph.addEdge(edgeId(taskNodeId, assetNodeId, "writes"), taskNodeId, assetNodeId,
                                LineageGraphEdgeType.WRITES, null, null, List.of());
                    }
                    if (relatedAsset.getModelId() != null && visited.add(relatedAsset.getModelId())) {
                        next.add(relatedAsset.getModelId());
                    }
                }
            }
            frontier = next;
        }
    }

    private void walkField(
            UUID rootFieldId,
            LineageGraphNodeSide side,
            int maximumDepth,
            GraphBuilder graph
    ) {
        Set<UUID> frontier = Set.of(rootFieldId);
        Set<UUID> visited = new HashSet<>();
        visited.add(rootFieldId);
        for (int level = 1; level <= maximumDepth && !frontier.isEmpty() && !graph.truncated(); level++) {
            List<TaskLineageAssetField> matchingFields = assetFieldRepository.findCurrentByModelFieldIdIn(frontier);
            if (matchingFields.isEmpty()) break;
            Set<UUID> snapshotIds = matchingFields.stream().map(TaskLineageAssetField::getSnapshotId).collect(Collectors.toSet());
            SnapshotBundle bundle = loadBundle(snapshotIds, true);
            Set<UUID> next = new LinkedHashSet<>();
            for (TaskLineageAssetField anchorField : matchingFields) {
                if (graph.truncated()) break;
                TaskLineageAsset anchorAsset = bundle.assets().get(anchorField.getAssetId());
                if (anchorAsset == null || (side == LineageGraphNodeSide.UPSTREAM
                        ? anchorAsset.getRole() != LineageAssetRole.OUTPUT
                        : anchorAsset.getRole() != LineageAssetRole.INPUT)) {
                    continue;
                }
                TaskLineageSnapshot snapshot = bundle.snapshots().get(anchorField.getSnapshotId());
                if (snapshot == null || flowCoverage(anchorAsset,
                        bundle.assetsBySnapshotFlow().getOrDefault(
                                new SnapshotFlow(snapshot.getId(), anchorAsset.getFlowKey()), List.of()), snapshot)
                        == LineageCoverage.MODEL_ONLY) continue;
                if (side == LineageGraphNodeSide.DOWNSTREAM
                        && bundle.edges().stream().noneMatch(edge -> edge.getSourceAssetFieldId().equals(anchorField.getId()))
                        && bundle.usages().stream().noneMatch(usage -> usage.getAssetFieldId().equals(anchorField.getId())
                                && usage.getFlowKey().equals(anchorAsset.getFlowKey()))) {
                    continue;
                }
                graph.includeCoverage(flowCoverage(anchorAsset,
                        bundle.assetsBySnapshotFlow().getOrDefault(
                                new SnapshotFlow(snapshot.getId(), anchorAsset.getFlowKey()), List.of()), snapshot));
                String taskId = taskNodeId(snapshot, anchorAsset.getFlowKey(), side);
                LineageWriteMode writeMode = bundle.assetsBySnapshotFlow().getOrDefault(
                                new SnapshotFlow(snapshot.getId(), anchorAsset.getFlowKey()), List.of()
                        ).stream()
                        .filter(asset -> asset.getRole() == LineageAssetRole.OUTPUT)
                        .map(TaskLineageAsset::getWriteMode)
                        .findFirst()
                        .orElse(null);
                graph.addNode(taskNode(
                        snapshot, anchorAsset.getFlowKey(), side, level,
                        bundle.tasks().get(snapshot.getTaskId()), writeMode
                ));
                LineageGraphNodeSide anchorSide = anchorField.getModelFieldId() != null
                        && anchorField.getModelFieldId().equals(rootFieldId) && level == 1
                        ? LineageGraphNodeSide.CURRENT : side;
                String anchorNodeId = fieldNodeId(anchorField, anchorSide);
                boolean anchorStale = isStale(anchorAsset, bundle.models(), anchorField, bundle.modelFields());
                if (anchorSide == LineageGraphNodeSide.CURRENT) {
                    if (anchorStale) graph.markStale(anchorNodeId);
                } else {
                    graph.addNode(fieldNode(
                            anchorField, anchorAsset, anchorSide, level, anchorStale,
                            bundle.models(), bundle.modelFields()
                    ));
                }
                addUsageEdge(bundle, anchorField, anchorAsset, taskId, side, level, graph);

                if (side == LineageGraphNodeSide.UPSTREAM) {
                    List<TaskLineageFieldEdge> incoming = bundle.edges().stream()
                            .filter(edge -> edge.getTargetAssetFieldId().equals(anchorField.getId()))
                            .toList();
                    if (incoming.isEmpty() && anchorField.getOutputEffect() != null
                            && anchorField.getOutputEffect() != LineageOutputFieldEffect.DERIVED) {
                        graph.addEdge(edgeId(taskId, anchorNodeId, "effect"), taskId, anchorNodeId,
                                LineageGraphEdgeType.FIELD_EFFECT, null, anchorField.getOutputEffect(), List.of());
                    }
                    for (TaskLineageFieldEdge edge : incoming) {
                        TaskLineageAssetField sourceField = bundle.fields().get(edge.getSourceAssetFieldId());
                        TaskLineageAsset sourceAsset = sourceField == null ? null : bundle.assets().get(sourceField.getAssetId());
                        if (sourceField == null || sourceAsset == null) continue;
                        String sourceNodeId = fieldNodeId(sourceField, side);
                        graph.addNode(fieldNode(
                                sourceField, sourceAsset, side, level,
                                isStale(sourceAsset, bundle.models(), sourceField, bundle.modelFields()),
                                bundle.models(), bundle.modelFields()
                        ));
                        addUsageEdge(bundle, sourceField, sourceAsset, taskId, side, level, graph);
                        graph.addEdge(edgeId(sourceNodeId, taskId, edge.getId().toString()), sourceNodeId, taskId,
                                LineageGraphEdgeType.DERIVES, edge.getDerivationType(), null, List.of());
                        graph.addEdge(edgeId(taskId, anchorNodeId, edge.getDerivationKey()), taskId, anchorNodeId,
                                LineageGraphEdgeType.DERIVES, edge.getDerivationType(), anchorField.getOutputEffect(), List.of());
                        if (sourceField.getModelFieldId() != null && visited.add(sourceField.getModelFieldId())) {
                            next.add(sourceField.getModelFieldId());
                        }
                    }
                } else {
                    List<TaskLineageFieldEdge> outgoing = bundle.edges().stream()
                            .filter(edge -> edge.getSourceAssetFieldId().equals(anchorField.getId()))
                            .toList();
                    for (TaskLineageFieldEdge edge : outgoing) {
                        TaskLineageAssetField targetField = bundle.fields().get(edge.getTargetAssetFieldId());
                        TaskLineageAsset targetAsset = targetField == null ? null : bundle.assets().get(targetField.getAssetId());
                        if (targetField == null || targetAsset == null) continue;
                        String targetNodeId = fieldNodeId(targetField, side);
                        graph.addNode(fieldNode(
                                targetField, targetAsset, side, level,
                                isStale(targetAsset, bundle.models(), targetField, bundle.modelFields()),
                                bundle.models(), bundle.modelFields()
                        ));
                        graph.addEdge(edgeId(anchorNodeId, taskId, edge.getId().toString()), anchorNodeId, taskId,
                                LineageGraphEdgeType.DERIVES, edge.getDerivationType(), null, List.of());
                        graph.addEdge(edgeId(taskId, targetNodeId, edge.getDerivationKey()), taskId, targetNodeId,
                                LineageGraphEdgeType.DERIVES, edge.getDerivationType(), targetField.getOutputEffect(), List.of());
                        if (targetField.getModelFieldId() != null && visited.add(targetField.getModelFieldId())) {
                            next.add(targetField.getModelFieldId());
                        }
                    }
                }
            }
            frontier = next;
        }
    }

    private void addFieldCoverageWithoutEdges(
            UUID modelId,
            LineageDirection direction,
            GraphBuilder graph
    ) {
        FieldCoverageFallback fallback = fieldCoverageFallback(modelId, direction);
        graph.includeCoverage(fallback.coverage());
        if (fallback.warning() != null) graph.warn(fallback.warning());
    }

    private FieldCoverageFallback fieldCoverageFallback(UUID modelId, LineageDirection direction) {
        List<TaskLineageAsset> assets = assetRepository.findCurrentByModelIdIn(Set.of(modelId)).stream()
                .filter(asset -> (direction.includesUpstream() && asset.getRole() == LineageAssetRole.OUTPUT)
                        || (direction.includesDownstream() && asset.getRole() == LineageAssetRole.INPUT))
                .toList();
        if (assets.isEmpty()) return new FieldCoverageFallback(null, "当前字段没有可展示的字段血缘关系");
        Set<UUID> snapshotIds = assets.stream().map(TaskLineageAsset::getSnapshotId).collect(Collectors.toSet());
        List<TaskLineageSnapshot> snapshots = snapshotRepository.findAllByIdIn(snapshotIds);
        Map<UUID, TaskLineageSnapshot> byId = snapshots.stream()
                .collect(Collectors.toMap(TaskLineageSnapshot::getId, Function.identity()));
        List<LineageCoverage> coverages = assets.stream().map(asset -> {
            TaskLineageSnapshot snapshot = byId.get(asset.getSnapshotId());
            return snapshot == null ? null : asset.getFlowCoverage() == null
                    ? snapshot.getCoverage() : asset.getFlowCoverage();
        }).filter(Objects::nonNull).toList();
        LineageCoverage coverage = coverages.stream().min(Comparator.comparingInt(Enum::ordinal)).orElse(null);
        boolean modelOnly = coverages.stream().anyMatch(value -> value == LineageCoverage.MODEL_ONLY);
        return new FieldCoverageFallback(coverage, modelOnly
                ? "当前字段所在路径只有表级血缘，尚未生成字段来源关系"
                : "当前字段没有可展示的字段血缘关系");
    }

    private SnapshotBundle loadBundle(Set<UUID> snapshotIds, boolean includeFields) {
        if (snapshotIds.isEmpty()) return SnapshotBundle.empty();
        Map<UUID, TaskLineageSnapshot> snapshots = snapshotRepository.findAllByIdIn(snapshotIds).stream()
                .filter(snapshot -> snapshot.getRetiredAt() == null)
                .collect(Collectors.toMap(TaskLineageSnapshot::getId, Function.identity()));
        Set<UUID> currentIds = snapshots.keySet();
        List<TaskLineageAsset> assetList = assetRepository.findAllBySnapshotIdIn(currentIds);
        Map<UUID, TaskLineageAsset> assets = assetList.stream()
                .collect(Collectors.toMap(TaskLineageAsset::getId, Function.identity()));
        Map<SnapshotFlow, List<TaskLineageAsset>> assetsByFlow = assetList.stream()
                .collect(Collectors.groupingBy(asset -> new SnapshotFlow(asset.getSnapshotId(), asset.getFlowKey())));
        Set<UUID> taskIds = snapshots.values().stream().map(TaskLineageSnapshot::getTaskId).collect(Collectors.toSet());
        Map<UUID, DataTask> tasks = taskRepository.findAllById(taskIds).stream()
                .collect(Collectors.toMap(DataTask::getId, Function.identity()));
        Set<UUID> modelIds = assetList.stream().map(TaskLineageAsset::getModelId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, DataModel> models = modelRepository.findAllById(modelIds).stream()
                .collect(Collectors.toMap(DataModel::getId, Function.identity()));
        Set<UUID> dataSourceIds = assetList.stream().map(TaskLineageAsset::getDataSourceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        models.values().stream().map(DataModel::getStorageDataSourceId).forEach(dataSourceIds::add);
        Map<UUID, DataSource> dataSources = dataSourceRepository.findAllById(dataSourceIds).stream()
                .collect(Collectors.toMap(DataSource::getId, Function.identity()));
        if (!includeFields) {
            return new SnapshotBundle(
                    snapshots, assets, assetsByFlow, Map.of(), List.of(), List.of(),
                    tasks, models, Map.of(), dataSources
            );
        }
        List<TaskLineageAssetField> fieldList = assetFieldRepository.findAllBySnapshotIdIn(currentIds);
        Map<UUID, TaskLineageAssetField> fields = fieldList.stream()
                .collect(Collectors.toMap(TaskLineageAssetField::getId, Function.identity()));
        Set<UUID> modelFieldIds = fieldList.stream().map(TaskLineageAssetField::getModelFieldId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, DataModelField> modelFields = modelFieldRepository.findAllById(modelFieldIds).stream()
                .collect(Collectors.toMap(DataModelField::getId, Function.identity()));
        return new SnapshotBundle(
                snapshots, assets, assetsByFlow, fields,
                fieldEdgeRepository.findAllBySnapshotIdIn(currentIds),
                fieldUsageRepository.findAllBySnapshotIdIn(currentIds),
                tasks, models, modelFields, dataSources
        );
    }

    private static void addUsageEdge(
            SnapshotBundle bundle,
            TaskLineageAssetField field,
            TaskLineageAsset asset,
            String taskNodeId,
            LineageGraphNodeSide side,
            int depth,
            GraphBuilder graph
    ) {
        addUsageEdge(bundle, field, asset, taskNodeId, side, depth, graph, Set.of());
    }

    private static void addUsageEdge(
            SnapshotBundle bundle,
            TaskLineageAssetField field,
            TaskLineageAsset asset,
            String taskNodeId,
            LineageGraphNodeSide side,
            int depth,
            GraphBuilder graph,
            Set<String> focusKeys
    ) {
        List<LineageFieldUsageType> usages = bundle.usages().stream()
                .filter(usage -> usage.getSnapshotId().equals(field.getSnapshotId())
                        && usage.getFlowKey().equals(asset.getFlowKey())
                        && usage.getAssetFieldId().equals(field.getId()))
                .map(TaskLineageFieldUsage::getUsageType)
                .distinct()
                .sorted()
                .toList();
        if (usages.isEmpty()) return;
        String nodeId = fieldNodeId(field, side);
        graph.addNode(fieldNode(
                field, asset, side, depth,
                isStale(asset, bundle.models(), field, bundle.modelFields()),
                bundle.models(), bundle.modelFields()
        ), focusKeys, false);
        graph.addEdge(
                edgeId(nodeId, taskNodeId, "usage:" + field.getId()), nodeId, taskNodeId,
                LineageGraphEdgeType.FIELD_EFFECT, null, null, usages, focusKeys
        );
    }

    private DataModel requireModel(UUID modelId) {
        return modelRepository.findById(modelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型不存在"));
    }

    private static void requireDepth(int depth) {
        if (depth < 1 || depth > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "血缘层级只支持 1 或 2");
        }
    }

    private static boolean isStale(TaskLineageAsset asset, Map<UUID, DataModel> models) {
        if (asset.getAssetKind() != LineageAssetKind.MODEL) return false;
        DataModel model = models.get(asset.getModelId());
        return model == null || asset.getModelSchemaVersion() == null
                || model.getSchemaVersion() != asset.getModelSchemaVersion();
    }

    private static boolean isStale(
            TaskLineageAsset asset,
            Map<UUID, DataModel> models,
            TaskLineageAssetField field,
            Map<UUID, DataModelField> modelFields
    ) {
        return isStale(asset, models) || (field.getModelFieldId() != null && !modelFields.containsKey(field.getModelFieldId()));
    }

    private static LineageGraphNodeResponse modelNode(
            DataModel model,
            LineageGraphNodeSide side,
            int depth,
            boolean stale
    ) {
        return new LineageGraphNodeResponse(
                modelNodeId(model.getId(), side), LineageGraphNodeKind.MODEL, side, depth,
                model.getName(), model.getCode(), model.getId(), null, null,
                model.getStorageDataSourceId(), null, null, null, stale
        );
    }

    private static LineageGraphNodeResponse assetNode(
            TaskLineageAsset asset,
            LineageGraphNodeSide side,
            int depth,
            boolean stale,
            Map<UUID, DataModel> models,
            Map<UUID, DataSource> dataSources
    ) {
        if (asset.getAssetKind() == LineageAssetKind.MODEL) {
            DataModel current = models.get(asset.getModelId());
            return new LineageGraphNodeResponse(
                    assetNodeId(asset, side), LineageGraphNodeKind.MODEL, side, depth,
                    current == null ? asset.getModelNameSnapshot() : current.getName(),
                    current == null ? asset.getModelCodeSnapshot() : current.getCode(),
                    asset.getModelId(), null, null,
                    current == null ? null : current.getStorageDataSourceId(),
                    null, null, asset.getWriteMode(), stale
            );
        }
        if (asset.getAssetKind() == LineageAssetKind.EXTERNAL_RESOURCE) {
            DataSource currentSource = dataSources.get(asset.getDataSourceId());
            String owner = currentSource == null
                    ? asset.getDataSourceNameSnapshot() : currentSource.getName();
            String subtitle = java.util.stream.Stream.of(
                            asset.getExternalResourceType() == null ? null : asset.getExternalResourceType().name(),
                            owner)
                    .filter(value -> value != null && !value.isBlank())
                    .collect(Collectors.joining(" · "));
            return new LineageGraphNodeResponse(
                    assetNodeId(asset, side), LineageGraphNodeKind.EXTERNAL_RESOURCE, side, depth,
                    asset.getResourceNameSnapshot(), subtitle, null, null, null,
                    asset.getDataSourceId(), null, null, asset.getWriteMode(), false,
                    asset.getExternalResourceType(), asset.getResourceId()
            );
        }
        DataSource currentSource = dataSources.get(asset.getDataSourceId());
        return new LineageGraphNodeResponse(
                assetNodeId(asset, side), LineageGraphNodeKind.JDBC_TABLE, side, depth,
                asset.getPhysicalTableName(), jdbcSubtitle(asset, currentSource), null, null, null,
                asset.getDataSourceId(), null, null, asset.getWriteMode(), false
        );
    }

    private static LineageGraphNodeResponse fieldNode(
            TaskLineageAssetField field,
            TaskLineageAsset asset,
            LineageGraphNodeSide side,
            int depth,
            boolean stale,
            Map<UUID, DataModel> models,
            Map<UUID, DataModelField> modelFields
    ) {
        DataModel currentModel = models.get(asset.getModelId());
        DataModelField currentField = modelFields.get(field.getModelFieldId());
        String owner = currentModel != null ? currentModel.getName()
                : asset.getAssetKind() == LineageAssetKind.MODEL
                    ? asset.getModelNameSnapshot()
                    : asset.getAssetKind() == LineageAssetKind.EXTERNAL_RESOURCE
                        ? asset.getResourceNameSnapshot() : asset.getPhysicalTableName();
        String fieldName = currentField == null ? field.getFieldNameSnapshot() : currentField.getName();
        String fieldCode = currentField == null ? field.getFieldCodeSnapshot() : currentField.getCode();
        return new LineageGraphNodeResponse(
                fieldNodeId(field, side), LineageGraphNodeKind.FIELD, side, depth,
                fieldName, owner + " · " + fieldCode,
                asset.getModelId(), field.getModelFieldId(), null,
                currentModel == null ? asset.getDataSourceId() : currentModel.getStorageDataSourceId(),
                null, null, asset.getWriteMode(), stale,
                asset.getExternalResourceType(), asset.getResourceId(), null, null, null, null,
                fieldOwner(field, asset, side, currentModel), false, List.of()
        );
    }

    private static LineageFieldOwnerResponse fieldOwner(
            TaskLineageAssetField field,
            TaskLineageAsset asset,
            LineageGraphNodeSide side,
            DataModel currentModel
    ) {
        LineageGraphNodeKind kind = switch (asset.getAssetKind()) {
            case MODEL -> LineageGraphNodeKind.MODEL;
            case JDBC_TABLE -> LineageGraphNodeKind.JDBC_TABLE;
            case EXTERNAL_RESOURCE -> LineageGraphNodeKind.EXTERNAL_RESOURCE;
        };
        String label = switch (asset.getAssetKind()) {
            case MODEL -> currentModel == null ? asset.getModelNameSnapshot() : currentModel.getName();
            case JDBC_TABLE -> asset.getPhysicalTableName();
            case EXTERNAL_RESOURCE -> asset.getResourceNameSnapshot();
        };
        String subtitle = switch (asset.getAssetKind()) {
            case MODEL -> currentModel == null ? asset.getModelCodeSnapshot() : currentModel.getCode();
            case JDBC_TABLE -> jdbcSubtitle(asset, null);
            case EXTERNAL_RESOURCE -> asset.getExternalResourceType() == null
                    ? null : asset.getExternalResourceType().name();
        };
        return new LineageFieldOwnerResponse(
                assetNodeId(asset, side), kind, label, subtitle, field.getSortOrder()
        );
    }

    private static LineageGraphNodeResponse taskNode(
            TaskLineageSnapshot snapshot,
            String flowKey,
            LineageGraphNodeSide side,
            int depth,
            DataTask task,
            LineageWriteMode writeMode
    ) {
        return new LineageGraphNodeResponse(
                taskNodeId(snapshot, flowKey, side), LineageGraphNodeKind.TASK, side, depth,
                task == null ? snapshot.getTaskNameSnapshot() : task.getName(),
                (task == null ? snapshot.getTaskType() : task.getType()).name(), null, null,
                snapshot.getTaskId(), null, task == null ? null : task.getStatus(),
                snapshot.getDefinitionVersion(), writeMode, false
        );
    }

    static LineageGraphNodeResponse dataServiceNode(
            StandardDataServiceLineageService.StandardServiceLineage item,
            LineageGraphNodeSide side,
            int depth
    ) {
        return new LineageGraphNodeResponse(
                dataServiceNodeId(item.service().getId(), side),
                LineageGraphNodeKind.DATA_SERVICE,
                side,
                depth,
                item.service().getName(),
                item.service().getCode(),
                null, null, null, null, null,
                item.definition().getVersion(), null, item.stale(), null, null,
                item.service().getId(), item.service().getType(), item.service().getStatus(),
                item.service().getRoutePath()
        );
    }

    private static String modelNodeId(UUID modelId, LineageGraphNodeSide side) {
        return "model:" + modelId + ':' + side;
    }

    private static String fieldNodeId(UUID fieldId, LineageGraphNodeSide side) {
        return "field:" + fieldId + ':' + side;
    }

    private static String fieldNodeId(TaskLineageAssetField field, LineageGraphNodeSide side) {
        return field.getModelFieldId() == null
                ? "physical-field:" + field.getId() + ':' + side
                : fieldNodeId(field.getModelFieldId(), side);
    }

    private static String assetNodeId(TaskLineageAsset asset, LineageGraphNodeSide side) {
        if (asset.getModelId() != null) return modelNodeId(asset.getModelId(), side);
        if (asset.getAssetKind() == LineageAssetKind.EXTERNAL_RESOURCE) {
            return "external:" + asset.getExternalResourceType() + ':'
                    + Objects.toString(asset.getResourceId(), "") + ':'
                    + asset.getResourceKey() + ':' + side;
        }
        return "jdbc:" + asset.getDataSourceId() + ':'
                + Objects.toString(asset.getCatalogName(), "") + ':'
                + Objects.toString(asset.getSchemaName(), "") + ':'
                + asset.getPhysicalTableName() + ':' + side;
    }

    private static String taskNodeId(
            TaskLineageSnapshot snapshot,
            String flowKey,
            LineageGraphNodeSide side
    ) {
        return "task:" + snapshot.getId() + ':' + flowKey + ':' + side;
    }

    static String dataServiceNodeId(UUID serviceId, LineageGraphNodeSide side) {
        return "data-service:" + serviceId + ':' + side;
    }

    private static String edgeId(String source, String target, String discriminator) {
        return "edge:" + Integer.toUnsignedString(Objects.hash(source, target, discriminator), 36)
                + ':' + source + ':' + target;
    }

    private static String jdbcSubtitle(TaskLineageAsset asset, DataSource currentSource) {
        String dataSourceName = currentSource == null
                ? asset.getDataSourceNameSnapshot() : currentSource.getName();
        String physicalLocation = java.util.stream.Stream.of(
                        asset.getCatalogName(), asset.getSchemaName(), asset.getPhysicalTableName())
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining("."));
        return physicalLocation.isBlank() ? dataSourceName : dataSourceName + " · " + physicalLocation;
    }

    private static LineageCoverage flowCoverage(
            TaskLineageAsset anchor,
            List<TaskLineageAsset> flowAssets,
            TaskLineageSnapshot snapshot
    ) {
        return flowAssets.stream()
                .filter(asset -> asset.getRole() == LineageAssetRole.OUTPUT)
                .map(TaskLineageAsset::getFlowCoverage)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(anchor.getFlowCoverage() == null
                        ? snapshot.getCoverage() : anchor.getFlowCoverage());
    }

    private record SnapshotFlow(UUID snapshotId, String flowKey) {
    }

    private record FieldCoverageFallback(LineageCoverage coverage, String warning) {
    }

    private record SnapshotBundle(
            Map<UUID, TaskLineageSnapshot> snapshots,
            Map<UUID, TaskLineageAsset> assets,
            Map<SnapshotFlow, List<TaskLineageAsset>> assetsBySnapshotFlow,
            Map<UUID, TaskLineageAssetField> fields,
            List<TaskLineageFieldEdge> edges,
            List<TaskLineageFieldUsage> usages,
            Map<UUID, DataTask> tasks,
            Map<UUID, DataModel> models,
            Map<UUID, DataModelField> modelFields,
            Map<UUID, DataSource> dataSources
    ) {
        private static SnapshotBundle empty() {
            return new SnapshotBundle(
                    Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of()
            );
        }
    }

    private static final class GraphBuilder {
        private final String rootNodeId;
        private final LineageGranularity granularity;
        private final Map<String, LineageGraphNodeResponse> nodes = new LinkedHashMap<>();
        private final Map<String, LineageGraphEdgeResponse> edges = new LinkedHashMap<>();
        private final LinkedHashSet<String> warnings = new LinkedHashSet<>();
        private final Map<String, LineageCoverage> focusCoverages = new LinkedHashMap<>();
        private final Set<String> truncatedFocusKeys = new LinkedHashSet<>();
        private LineageCoverage coverage;
        private boolean truncated;

        private GraphBuilder(String rootNodeId, LineageGranularity granularity) {
            this.rootNodeId = rootNodeId;
            this.granularity = granularity;
        }

        private void addNode(LineageGraphNodeResponse node) {
            addNode(node, Set.of(), false);
        }

        private void addNode(LineageGraphNodeResponse node, Set<String> focusKeys, boolean focusRoot) {
            LineageGraphNodeResponse existing = nodes.get(node.id());
            if (existing != null) {
                Set<String> mergedKeys = union(existing.focusFieldKeys(), focusKeys);
                nodes.put(node.id(), copyNode(existing, existing.stale() || node.stale(),
                        existing.focusRoot() || focusRoot, mergedKeys));
                return;
            }
            if (nodes.size() >= MAXIMUM_NODES) {
                truncated = true;
                truncatedFocusKeys.addAll(focusKeys);
                warnings.add("血缘节点超过 " + MAXIMUM_NODES + " 个，当前图已截断");
                return;
            }
            nodes.put(node.id(), copyNode(node, node.stale(), focusRoot, focusKeys));
            if (node.stale()) warnings.add("部分血缘基于旧模型结构，已标记为陈旧");
        }

        private void addFocus(String nodeId, Set<String> focusKeys) {
            LineageGraphNodeResponse node = nodes.get(nodeId);
            if (node == null) return;
            nodes.put(nodeId, copyNode(node, node.stale(), node.focusRoot(),
                    union(node.focusFieldKeys(), focusKeys)));
        }

        private void markStale(String nodeId) {
            LineageGraphNodeResponse node = nodes.get(nodeId);
            if (node == null || node.stale()) return;
            nodes.put(nodeId, copyNode(node, true, node.focusRoot(), new LinkedHashSet<>(node.focusFieldKeys())));
            warnings.add("部分血缘基于旧模型结构，已标记为陈旧");
        }

        private void addEdge(
                String id,
                String source,
                String target,
                LineageGraphEdgeType type,
                LineageFieldDerivationType derivationType,
                LineageOutputFieldEffect outputEffect,
                List<LineageFieldUsageType> usages
        ) {
            addEdge(id, source, target, type, derivationType, outputEffect, usages, Set.of());
        }

        private void addEdge(
                String id,
                String source,
                String target,
                LineageGraphEdgeType type,
                LineageFieldDerivationType derivationType,
                LineageOutputFieldEffect outputEffect,
                List<LineageFieldUsageType> usages,
                Set<String> focusKeys
        ) {
            if (!nodes.containsKey(source) || !nodes.containsKey(target)) return;
            LineageGraphEdgeResponse existing = edges.get(id);
            if (existing != null) {
                edges.put(id, new LineageGraphEdgeResponse(
                        existing.id(), existing.source(), existing.target(), existing.type(),
                        existing.derivationType(), existing.outputEffect(), existing.usages(),
                        List.copyOf(union(existing.focusFieldKeys(), focusKeys))
                ));
                return;
            }
            if (edges.size() >= MAXIMUM_EDGES) {
                truncated = true;
                truncatedFocusKeys.addAll(focusKeys);
                warnings.add("血缘关系超过 " + MAXIMUM_EDGES + " 条，当前图已截断");
                return;
            }
            edges.put(id, new LineageGraphEdgeResponse(
                    id, source, target, type, derivationType, outputEffect, usages, List.copyOf(focusKeys)
            ));
        }

        private void includeCoverage(LineageCoverage value) {
            if (value == null) return;
            coverage = coverage == null || value.ordinal() < coverage.ordinal() ? value : coverage;
            if (value == LineageCoverage.FIELD_PARTIAL) warnings.add("部分路径只有不完整的字段血缘");
        }

        private void includeCoverage(Set<String> focusKeys, LineageCoverage value) {
            includeCoverage(value);
            if (value == null) return;
            for (String focusKey : focusKeys) {
                LineageCoverage current = focusCoverages.get(focusKey);
                if (current == null || value.ordinal() < current.ordinal()) {
                    focusCoverages.put(focusKey, value);
                }
            }
        }

        private LineageCoverage focusCoverage(String focusKey) {
            return focusCoverages.get(focusKey);
        }

        private LineageFocusFieldResponse focusField(DataModelField field, String focusKey) {
            boolean hasLineage = edges.values().stream().anyMatch(edge -> edge.focusFieldKeys().contains(focusKey));
            boolean focusTruncated = truncatedFocusKeys.contains(focusKey);
            List<String> fieldWarnings = new ArrayList<>();
            if (!hasLineage) fieldWarnings.add("该字段暂无可展示的字段血缘");
            if (focusTruncated) fieldWarnings.add("该字段路径因图规模限制已截断");
            return new LineageFocusFieldResponse(
                    focusKey, field.getId(), field.getCode(), field.getName(), field.getSortOrder(),
                    focusCoverages.get(focusKey), hasLineage, focusTruncated, fieldWarnings
            );
        }

        private void warn(String warning) { warnings.add(warning); }
        private List<LineageGraphNodeResponse> nodes() { return List.copyOf(nodes.values()); }
        private boolean truncated() { return truncated; }
        private LineageCoverage coverage() { return coverage; }

        private LineageGraphResponse response() {
            return new LineageGraphResponse(
                    rootNodeId, granularity, coverage, truncated, List.copyOf(warnings),
                    List.copyOf(nodes.values()), List.copyOf(edges.values())
            );
        }

        private static LineageGraphNodeResponse copyNode(
                LineageGraphNodeResponse node,
                boolean stale,
                boolean focusRoot,
                Set<String> focusKeys
        ) {
            return new LineageGraphNodeResponse(
                    node.id(), node.kind(), node.side(), node.depth(), node.label(), node.subtitle(),
                    node.modelId(), node.modelFieldId(), node.taskId(), node.dataSourceId(), node.taskStatus(),
                    node.definitionVersion(), node.writeMode(), stale, node.externalResourceType(), node.resourceId(),
                    node.dataServiceId(), node.dataServiceType(), node.dataServiceStatus(), node.routePath(),
                    node.fieldOwner(), focusRoot, List.copyOf(focusKeys)
            );
        }

        private static Set<String> union(Collection<String> first, Collection<String> second) {
            Set<String> result = new LinkedHashSet<>(first);
            result.addAll(second);
            return result;
        }
    }
}

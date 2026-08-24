package cn.superhuang.data.scalpel.business.lineage.service;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;
import cn.superhuang.data.scalpel.business.lineage.web.request.LineageDirection;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGranularity;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageFieldGraphResponse;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageFocusFieldResponse;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGraphEdgeResponse;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGraphEdgeType;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGraphNodeKind;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGraphNodeResponse;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGraphNodeSide;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGraphResponse;
import cn.superhuang.data.scalpel.business.model.domain.DataModelField;
import cn.superhuang.data.scalpel.business.model.repository.DataModelFieldRepository;
import cn.superhuang.data.scalpel.business.service.domain.DataService;
import cn.superhuang.data.scalpel.business.service.repository.DataServiceRepository;
import cn.superhuang.data.scalpel.contract.service.DataServiceType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Builds service-rooted graphs by attaching a standard service to the current model lineage. */
@Service
public class DataServiceLineageQueryService {

    private static final int MAXIMUM_NODES = 200;
    private static final int MAXIMUM_EDGES = 600;
    private static final int DEFAULT_FIELD_COUNT = 20;

    private final DataServiceRepository serviceRepository;
    private final DataModelFieldRepository fieldRepository;
    private final StandardDataServiceLineageService standardServiceLineageService;
    private final ModelLineageQueryService modelLineageQueryService;

    public DataServiceLineageQueryService(
            DataServiceRepository serviceRepository,
            DataModelFieldRepository fieldRepository,
            StandardDataServiceLineageService standardServiceLineageService,
            ModelLineageQueryService modelLineageQueryService
    ) {
        this.serviceRepository = serviceRepository;
        this.fieldRepository = fieldRepository;
        this.standardServiceLineageService = standardServiceLineageService;
        this.modelLineageQueryService = modelLineageQueryService;
    }

    @Transactional(readOnly = true)
    public LineageGraphResponse tableLineage(UUID serviceId, int depth) {
        requireDepth(depth);
        DataService service = requireService(serviceId);
        if (service.getType() != DataServiceType.STANDARD_TABLE) {
            return empty(service, LineageGranularity.TABLE, "当前服务类型暂未接入血缘");
        }
        StandardDataServiceLineageService.StandardServiceLineage lineage =
                standardServiceLineageService.findCurrent(service).orElse(null);
        if (lineage == null) return empty(service, LineageGranularity.TABLE, "标准服务定义尚未配置");
        if (lineage.model() == null) return empty(service, LineageGranularity.TABLE, "关联模型不存在");
        return attach(
                service,
                lineage,
                modelLineageQueryService.tableLineage(
                        lineage.model().getId(), LineageDirection.UPSTREAM, depth
                ),
                null
        );
    }

    @Transactional(readOnly = true)
    public LineageGraphResponse fieldLineage(UUID serviceId, UUID fieldId, int depth) {
        return fieldLineages(serviceId, List.of(fieldId), depth).graph();
    }

    @Transactional(readOnly = true)
    public LineageFieldGraphResponse fieldLineages(UUID serviceId, List<UUID> requestedFieldIds, int depth) {
        requireDepth(depth);
        DataService service = requireService(serviceId);
        if (service.getType() != DataServiceType.STANDARD_TABLE) {
            return new LineageFieldGraphResponse(
                    empty(service, LineageGranularity.FIELD, "当前服务类型暂未接入血缘"), List.of());
        }
        StandardDataServiceLineageService.StandardServiceLineage lineage =
                standardServiceLineageService.findCurrent(service).orElse(null);
        if (lineage == null) return new LineageFieldGraphResponse(
                empty(service, LineageGranularity.FIELD, "标准服务定义尚未配置"), List.of());
        if (lineage.model() == null) return new LineageFieldGraphResponse(
                empty(service, LineageGranularity.FIELD, "关联模型不存在"), List.of());
        List<DataModelField> selectedFields = selectFields(lineage, requestedFieldIds);
        List<UUID> exposedIds = selectedFields.stream().map(DataModelField::getId)
                .filter(lineage::exposes).toList();
        LineageFieldGraphResponse modelResult = modelLineageQueryService.fieldLineages(
                lineage.model().getId(), exposedIds, LineageDirection.UPSTREAM, depth
        );
        LineageGraphResponse graph = attachFields(service, lineage, modelResult.graph());
        Map<UUID, LineageFocusFieldResponse> modelSummaries = modelResult.focusFields().stream()
                .filter(item -> item.modelFieldId() != null)
                .collect(java.util.stream.Collectors.toMap(LineageFocusFieldResponse::modelFieldId, item -> item));
        List<LineageFocusFieldResponse> summaries = selectedFields.stream().map(field -> {
            if (!lineage.exposes(field.getId())) {
                return new LineageFocusFieldResponse(
                        field.getId().toString(), field.getId(), field.getCode(), field.getName(), field.getSortOrder(),
                        null, false, false, List.of("当前字段未被服务实际暴露")
                );
            }
            LineageFocusFieldResponse summary = modelSummaries.get(field.getId());
            if (summary == null) return new LineageFocusFieldResponse(
                    field.getId().toString(), field.getId(), field.getCode(), field.getName(), field.getSortOrder(),
                    LineageCoverage.FIELD_COMPLETE, true, graph.truncated(), List.of());
            return new LineageFocusFieldResponse(
                    summary.fieldKey(), summary.modelFieldId(), summary.code(), summary.name(), summary.sortOrder(),
                    worst(summary.coverage(), LineageCoverage.FIELD_COMPLETE), true,
                    summary.truncated() || graph.truncated(), summary.warnings()
            );
        }).toList();
        return new LineageFieldGraphResponse(graph, summaries);
    }

    private static List<DataModelField> selectFields(
            StandardDataServiceLineageService.StandardServiceLineage lineage,
            List<UUID> requestedFieldIds
    ) {
        List<DataModelField> available = lineage.fields();
        if (requestedFieldIds == null) {
            return available.stream().filter(field -> lineage.exposes(field.getId()))
                    .limit(DEFAULT_FIELD_COUNT).toList();
        }
        if (requestedFieldIds.isEmpty()) return List.of();
        Set<UUID> requested = new LinkedHashSet<>(requestedFieldIds);
        List<DataModelField> selected = available.stream().filter(field -> requested.contains(field.getId())).toList();
        if (selected.size() != requested.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "部分模型字段不存在或不属于当前服务关联模型");
        }
        return selected;
    }

    private static LineageGraphResponse attachFields(
            DataService service,
            StandardDataServiceLineageService.StandardServiceLineage lineage,
            LineageGraphResponse modelGraph
    ) {
        String serviceNodeId = ModelLineageQueryService.dataServiceNodeId(service.getId(), LineageGraphNodeSide.CURRENT);
        Set<String> allFocusKeys = modelGraph.nodes().stream().flatMap(node -> node.focusFieldKeys().stream())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        LineageGraphNodeResponse rawServiceNode = ModelLineageQueryService.dataServiceNode(
                lineage, LineageGraphNodeSide.CURRENT, 0);
        LineageGraphNodeResponse serviceNode = withFocus(rawServiceNode, false, allFocusKeys);
        List<LineageGraphNodeResponse> nodes = new ArrayList<>();
        nodes.add(serviceNode);
        modelGraph.nodes().stream().limit(MAXIMUM_NODES - 1L).map(DataServiceLineageQueryService::asUpstream)
                .forEach(nodes::add);
        Set<String> retainedNodeIds = nodes.stream().map(LineageGraphNodeResponse::id)
                .collect(java.util.stream.Collectors.toSet());
        List<LineageGraphEdgeResponse> edges = new ArrayList<>(modelGraph.edges().stream()
                .filter(edge -> retainedNodeIds.contains(edge.source()) && retainedNodeIds.contains(edge.target()))
                .limit(MAXIMUM_EDGES).toList());
        for (LineageGraphNodeResponse node : nodes) {
            if (edges.size() >= MAXIMUM_EDGES) break;
            if (node.kind() != LineageGraphNodeKind.FIELD || !node.focusRoot() || node.focusFieldKeys().isEmpty()) continue;
            edges.add(new LineageGraphEdgeResponse(
                    "edge:service-exposes:" + service.getId() + ':' + node.id(), node.id(), serviceNodeId,
                    LineageGraphEdgeType.EXPOSES, null, null, List.of(), node.focusFieldKeys()
            ));
        }
        boolean truncated = modelGraph.truncated() || modelGraph.nodes().size() >= MAXIMUM_NODES
                || modelGraph.edges().size() + modelGraph.nodes().stream().filter(LineageGraphNodeResponse::focusRoot).count()
                > MAXIMUM_EDGES;
        LinkedHashSet<String> warnings = new LinkedHashSet<>(modelGraph.warnings());
        warnings.addAll(lineage.warnings());
        if (truncated) warnings.add("服务字段血缘超过图规模限制，当前图已截断");
        return new LineageGraphResponse(serviceNodeId, LineageGranularity.FIELD,
                worst(modelGraph.coverage(), allFocusKeys.isEmpty() ? null : LineageCoverage.FIELD_COMPLETE),
                truncated, List.copyOf(warnings), nodes, edges);
    }

    private static LineageGraphResponse attach(
            DataService service,
            StandardDataServiceLineageService.StandardServiceLineage lineage,
            LineageGraphResponse modelGraph,
            LineageCoverage exposureCoverage
    ) {
        String serviceNodeId = ModelLineageQueryService.dataServiceNodeId(
                service.getId(), LineageGraphNodeSide.CURRENT
        );
        List<LineageGraphNodeResponse> nodes = new ArrayList<>();
        nodes.add(ModelLineageQueryService.dataServiceNode(
                lineage, LineageGraphNodeSide.CURRENT, 0
        ));
        boolean serviceTruncated = modelGraph.nodes().size() >= MAXIMUM_NODES;
        for (LineageGraphNodeResponse node : modelGraph.nodes().stream()
                .limit(MAXIMUM_NODES - 1L).toList()) {
            nodes.add(asUpstream(node));
        }
        Set<String> retainedNodeIds = nodes.stream().map(LineageGraphNodeResponse::id)
                .collect(java.util.stream.Collectors.toSet());
        List<LineageGraphEdgeResponse> edges = new ArrayList<>(modelGraph.edges().stream()
                .filter(edge -> retainedNodeIds.contains(edge.source()) && retainedNodeIds.contains(edge.target()))
                .toList());
        if (retainedNodeIds.contains(modelGraph.rootNodeId())) {
            edges.add(new LineageGraphEdgeResponse(
                    "edge:service-exposes:" + service.getId() + ':' + modelGraph.rootNodeId(),
                    modelGraph.rootNodeId(), serviceNodeId, LineageGraphEdgeType.EXPOSES,
                    null, null, List.of()
            ));
        }
        LinkedHashSet<String> warnings = new LinkedHashSet<>(modelGraph.warnings());
        warnings.addAll(lineage.warnings());
        if (serviceTruncated) warnings.add("血缘节点超过 " + MAXIMUM_NODES + " 个，当前图已截断");
        LineageCoverage coverage = worst(modelGraph.coverage(), exposureCoverage);
        return new LineageGraphResponse(
                serviceNodeId, modelGraph.granularity(), coverage,
                modelGraph.truncated() || serviceTruncated,
                List.copyOf(warnings), nodes, edges
        );
    }

    private static LineageGraphNodeResponse asUpstream(LineageGraphNodeResponse node) {
        return new LineageGraphNodeResponse(
                node.id(), node.kind(), LineageGraphNodeSide.UPSTREAM, node.depth(),
                node.label(), node.subtitle(), node.modelId(), node.modelFieldId(), node.taskId(),
                node.dataSourceId(), node.taskStatus(), node.definitionVersion(), node.writeMode(),
                node.stale(), node.externalResourceType(), node.resourceId(), node.dataServiceId(),
                node.dataServiceType(), node.dataServiceStatus(), node.routePath(),
                node.fieldOwner(),
                node.focusRoot(), node.focusFieldKeys()
        );
    }

    private static LineageGraphNodeResponse withFocus(
            LineageGraphNodeResponse node, boolean focusRoot, Set<String> focusKeys
    ) {
        return new LineageGraphNodeResponse(
                node.id(), node.kind(), node.side(), node.depth(), node.label(), node.subtitle(), node.modelId(),
                node.modelFieldId(), node.taskId(), node.dataSourceId(), node.taskStatus(), node.definitionVersion(),
                node.writeMode(), node.stale(), node.externalResourceType(), node.resourceId(), node.dataServiceId(),
                node.dataServiceType(), node.dataServiceStatus(), node.routePath(), node.fieldOwner(),
                focusRoot, List.copyOf(focusKeys)
        );
    }

    private LineageGraphResponse empty(DataService service, LineageGranularity granularity, String warning) {
        return empty(service, granularity, List.of(warning), false);
    }

    private static LineageGraphResponse empty(
            DataService service,
            LineageGranularity granularity,
            List<String> warnings,
            boolean stale
    ) {
        String nodeId = ModelLineageQueryService.dataServiceNodeId(
                service.getId(), LineageGraphNodeSide.CURRENT
        );
        LineageGraphNodeResponse node = new LineageGraphNodeResponse(
                nodeId, LineageGraphNodeKind.DATA_SERVICE, LineageGraphNodeSide.CURRENT, 0,
                service.getName(), service.getCode(), null, null, null, null, null, null,
                null, stale, null, null, service.getId(), service.getType(), service.getStatus(),
                service.getRoutePath()
        );
        return new LineageGraphResponse(
                nodeId, granularity, null, false, warnings, List.of(node), List.of()
        );
    }

    private DataService requireService(UUID serviceId) {
        return serviceRepository.findById(serviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "数据服务不存在"));
    }

    private static void requireDepth(int depth) {
        if (depth < 1 || depth > 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "血缘层级只支持 1 或 2");
        }
    }

    private static LineageCoverage worst(LineageCoverage left, LineageCoverage right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.ordinal() < right.ordinal() ? left : right;
    }
}

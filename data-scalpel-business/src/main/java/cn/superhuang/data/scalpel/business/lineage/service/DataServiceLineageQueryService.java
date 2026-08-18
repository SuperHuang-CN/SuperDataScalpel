package cn.superhuang.data.scalpel.business.lineage.service;

import cn.superhuang.data.scalpel.business.lineage.domain.LineageCoverage;
import cn.superhuang.data.scalpel.business.lineage.web.request.LineageDirection;
import cn.superhuang.data.scalpel.business.lineage.web.response.LineageGranularity;
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
import java.util.Set;
import java.util.UUID;

/** Builds service-rooted graphs by attaching a standard service to the current model lineage. */
@Service
public class DataServiceLineageQueryService {

    private static final int MAXIMUM_NODES = 200;

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
        requireDepth(depth);
        DataService service = requireService(serviceId);
        if (service.getType() != DataServiceType.STANDARD_TABLE) {
            return empty(service, LineageGranularity.FIELD, "当前服务类型暂未接入血缘");
        }
        StandardDataServiceLineageService.StandardServiceLineage lineage =
                standardServiceLineageService.findCurrent(service).orElse(null);
        if (lineage == null) return empty(service, LineageGranularity.FIELD, "标准服务定义尚未配置");
        if (lineage.model() == null) return empty(service, LineageGranularity.FIELD, "关联模型不存在");
        DataModelField field = fieldRepository.findById(fieldId)
                .filter(item -> item.getModelId().equals(lineage.model().getId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "模型字段不存在"));
        if (!lineage.exposes(field.getId())) {
            LinkedHashSet<String> warnings = new LinkedHashSet<>(lineage.warnings());
            warnings.add("当前字段不在服务实际暴露字段中");
            return empty(service, LineageGranularity.FIELD, List.copyOf(warnings), lineage.stale());
        }
        return attach(
                service,
                lineage,
                modelLineageQueryService.fieldLineage(
                        lineage.model().getId(), field.getId(), LineageDirection.UPSTREAM, depth
                ),
                LineageCoverage.FIELD_COMPLETE
        );
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
                node.dataServiceType(), node.dataServiceStatus(), node.routePath()
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

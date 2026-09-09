package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CompilationIssue;
import cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TrackFindDwellNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.TrackSummaryStatistic;
import cn.superhuang.data.scalpel.contract.task.TrackSummaryStatisticKind;
import cn.superhuang.datascalpel.taskengine.contract.CanvasNodeCategory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CanvasGraphPlan {
    private final List<Entry> entries = new ArrayList<>();
    private final List<List<Integer>> predecessors = new ArrayList<>();
    private final List<List<Integer>> successors = new ArrayList<>();
    private final List<Integer> topologicalOrder = new ArrayList<>();
    private final List<CompilationIssue> canvasIssues = new ArrayList<>();
    private final Map<String, List<Integer>> entriesById = new LinkedHashMap<>();
    private final CanvasExecutionMode executionMode;
    private final String trialTargetNodeId;
    private final cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperatorRegistry nodeOperators =
            cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperators.builtInRegistry();

    private CanvasGraphPlan(
            CanvasDefinition definition,
            CanvasExecutionMode executionMode,
            String trialTargetNodeId
    ) {
        this.executionMode = executionMode == null ? CanvasExecutionMode.BATCH : executionMode;
        this.trialTargetNodeId = trialTargetNodeId;
        initializeNodes(definition);
        validateNodeIds();
        initializeEdges(definition);
        validateDegrees();
        buildTopologicalOrder();
        validateExecutionModeGraph();
    }

    public static CanvasGraphPlan create(CanvasDefinition definition) {
        return new CanvasGraphPlan(definition, CanvasExecutionMode.BATCH, null);
    }

    public static CanvasGraphPlan create(CanvasDefinition definition, CanvasExecutionMode executionMode) {
        return new CanvasGraphPlan(definition, executionMode, null);
    }

    public static CanvasGraphPlan createForTrial(
            CanvasDefinition definition,
            CanvasExecutionMode executionMode,
            String targetNodeId
    ) {
        return new CanvasGraphPlan(definition, executionMode, targetNodeId);
    }

    public CanvasNodeDefinition nodeAt(int index) {
        return entries.get(index).node();
    }

    List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public List<Integer> topologicalOrder() {
        return Collections.unmodifiableList(topologicalOrder);
    }

    public List<Integer> predecessorsOf(int index) {
        return Collections.unmodifiableList(predecessors.get(index));
    }

    List<CompilationIssue> canvasIssues() {
        return List.copyOf(canvasIssues);
    }

    private void initializeNodes(CanvasDefinition definition) {
        if (definition == null) {
            canvasIssues.add(CompilationIssue.canvas("CANVAS_REQUIRED", "Canvas 定义不能为空", "task.definition"));
            return;
        }
        if (definition.schemaVersion() == null
                || definition.schemaVersion() != CanvasDefinition.CURRENT_SCHEMA_VERSION) {
            canvasIssues.add(CompilationIssue.canvas(
                    "UNSUPPORTED_SCHEMA_VERSION",
                    "只支持 Canvas schemaVersion " + CanvasDefinition.CURRENT_SCHEMA_VERSION,
                    "schemaVersion"
            ));
        }
        int schemaMinorVersion = definition.effectiveSchemaMinorVersion();
        if (schemaMinorVersion < CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION
                || schemaMinorVersion > CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION) {
            canvasIssues.add(CompilationIssue.canvas(
                    "UNSUPPORTED_SCHEMA_MINOR_VERSION",
                    "只支持 Canvas schemaMinorVersion "
                            + CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION + " 到 "
                            + CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                    "schemaMinorVersion"
            ));
        }
        if (definition.nodes() == null) {
            canvasIssues.add(CompilationIssue.canvas("NODES_REQUIRED", "Canvas nodes 不能为空", "nodes"));
            return;
        }
        if (definition.nodes().isEmpty()) {
            canvasIssues.add(CompilationIssue.canvas("CANVAS_EMPTY", "画布中还没有节点", "nodes"));
        }
        for (int canvasIndex = 0; canvasIndex < definition.nodes().size(); canvasIndex++) {
            CanvasNodeDefinition node = definition.nodes().get(canvasIndex);
            if (node == null) {
                canvasIssues.add(CompilationIssue.canvas(
                        "NODE_REQUIRED", "Canvas 节点不能为空", "nodes[" + canvasIndex + "]"));
                continue;
            }
            MutableNodeCompilation result = new MutableNodeCompilation(node.id());
            Entry entry = new Entry(entries.size(), canvasIndex, node, result);
            entries.add(entry);
            predecessors.add(new ArrayList<>());
            successors.add(new ArrayList<>());
            validateCommonNode(entry);
            if (node.nodeType().introducedInMinorVersion() > schemaMinorVersion) {
                entry.result().error(
                        "NODE_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                        node.nodeType() + " 从 Canvas " + CanvasDefinition.CURRENT_SCHEMA_VERSION + "."
                                + node.nodeType().introducedInMinorVersion() + " 开始支持",
                        "type"
                );
            }
            validateMinorVersionFeatures(entry, schemaMinorVersion);
            if (!nodeOperators.supports(node.nodeType(), executionMode)) {
                entry.result().error(
                        "NODE_EXECUTION_MODE_NOT_SUPPORTED",
                        node.nodeType() + " 不支持 " + executionMode + " 执行模式",
                        "type"
                );
            }
            if (!blank(node.id())) {
                entriesById.computeIfAbsent(node.id(), ignored -> new ArrayList<>()).add(entry.index());
            }
        }
    }

    private static void validateMinorVersionFeatures(Entry entry, int schemaMinorVersion) {
        if (schemaMinorVersion < 41 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition within
                && within.configuration() != null && within.configuration().regions() != null)
            entry.result().error("SPATIAL_WITHIN_REGIONS_REQUIRE_SCHEMA_VERSION", "规则格网汇总区域从 Canvas 4.41 开始支持", "configuration.regions");
        cn.superhuang.data.scalpel.contract.task.SpatialTemporalSlicing slicing = switch (entry.node()) {
            case cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition bin -> bin.configuration() == null ? null : bin.configuration().temporalSlicing();
            case cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition within -> within.configuration() == null ? null : within.configuration().temporalSlicing();
            default -> null;
        };
        if (schemaMinorVersion < 40 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialPointClusterNodeDefinition cluster
                && cluster.configuration() != null && cluster.configuration().dbscan() != null)
            entry.result().error("SPATIAL_DBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION", "显式空间/Linear DBSCAN 从 Canvas 4.40 开始支持", "configuration.dbscan");
        if (schemaMinorVersion < 39 && slicing != null && slicing.calendar() != null)
            entry.result().error("SPATIAL_CALENDAR_WINDOW_REQUIRE_SCHEMA_VERSION", "日历时间切片从 Canvas 4.39 开始支持", "configuration.temporalSlicing.calendar");
        if (schemaMinorVersion < 38 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition bin
                && bin.configuration() != null && bin.configuration().planarGrid() != null)
            entry.result().error("SPATIAL_PLANAR_GRID_REQUIRE_SCHEMA_VERSION", "平面格网原点与范围从 Canvas 4.38 开始支持", "configuration.planarGrid");
        if (schemaMinorVersion < 37 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition within
                && within.configuration() != null && within.configuration().statistics() != null) {
            for (int i = 0; i < within.configuration().statistics().size(); i++) {
                if (within.configuration().statistics().get(i).requiresWeightedDispersionVersion())
                    entry.result().error("SPATIAL_WITHIN_WEIGHTED_DISPERSION_REQUIRE_SCHEMA_VERSION",
                            "交叠比例加权方差/标准差从 Canvas 4.37 开始支持", "configuration.statistics[" + i + "].weighting");
            }
        }
        for (String path : cn.superhuang.data.scalpel.contract.task.CanvasSpatialUnitVersions.unsupportedPaths(entry.node(), schemaMinorVersion))
            entry.result().error("SPATIAL_EXTENDED_UNITS_REQUIRE_SCHEMA_VERSION", "扩展距离/面积单位从 Canvas 4.36 开始支持", path);
        for (String path : cn.superhuang.data.scalpel.contract.task.CanvasSpatialUnitVersions.unsupportedDurationPaths(entry.node(), schemaMinorVersion))
            entry.result().error("SPATIAL_DURATION_WEEKS_REQUIRE_SCHEMA_VERSION", "固定周时长单位从 Canvas 4.47 开始支持", path);
        List<TrackSummaryStatistic> trackSummaries = switch (entry.node()) {
            case TrackReconstructNodeDefinition track -> track.configuration() == null ? null : track.configuration().summaryStatistics();
            case TrackFindDwellNodeDefinition dwell -> dwell.configuration() == null ? null : dwell.configuration().summaryStatistics();
            default -> List.of();
        };
        if (schemaMinorVersion < 35 && trackSummaries != null
                && trackSummaries.stream().anyMatch(s -> s != null && (s.kind() == TrackSummaryStatisticKind.COUNT_FIELD || s.kind() == TrackSummaryStatisticKind.ANY)))
            entry.result().error("TRACK_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION", "轨迹字段 Count/Any 从 Canvas 4.35 开始支持", "configuration.summaryStatistics");
        if (schemaMinorVersion < 34 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition binStatistics
                && binStatistics.configuration() != null && binStatistics.configuration().statistics() != null
                && binStatistics.configuration().statistics().stream().anyMatch(s -> s != null && (s.kind() == cn.superhuang.data.scalpel.contract.task.SpatialBinStatisticKind.COUNT_FIELD || s.kind() == cn.superhuang.data.scalpel.contract.task.SpatialBinStatisticKind.ANY)))
            entry.result().error("SPATIAL_BIN_FIELD_STATISTICS_REQUIRE_SCHEMA_VERSION", "格网字段 Count/Any 从 Canvas 4.34 开始支持", "configuration.statistics");
        if (schemaMinorVersion < 33 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition bins
                && bins.configuration() != null && (bins.configuration().binShape() == cn.superhuang.data.scalpel.contract.task.SpatialBinShape.H3 || bins.configuration().h3() != null))
            entry.result().error("SPATIAL_H3_REQUIRE_SCHEMA_VERSION", "H3 格网从 Canvas 4.33 开始支持", "configuration.h3");
        if (schemaMinorVersion < 32 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionNodeDefinition center
                && center.configuration() != null && center.configuration().analyses() != null
                && center.configuration().analyses().stream().anyMatch(a -> a != null && a.centralFeatureColumns() != null))
            entry.result().error("SPATIAL_CENTER_PROJECTION_REQUIRE_SCHEMA_VERSION", "中央要素字段投影从 Canvas 4.32 开始支持", "configuration.analyses");
        if (schemaMinorVersion < 31 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionNodeDefinition center
                && center.configuration() != null && (center.configuration().resultMode() != null || center.configuration().analyses() != null
                && center.configuration().analyses().stream().anyMatch(a -> a != null && a.outputTableName() != null)))
            entry.result().error("SPATIAL_CENTER_RESULTS_REQUIRE_SCHEMA_VERSION", "中心独立结果配置从 Canvas 4.31 开始支持", "configuration.resultMode");
        if (schemaMinorVersion < 30 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialNearestNodeDefinition nearest
                && nearest.configuration() != null && nearest.configuration().matching() != null)
            entry.result().error("SPATIAL_NEAREST_MATCHING_REQUIRE_SCHEMA_VERSION", "显式最近位置匹配从 Canvas 4.30 开始支持", "configuration.matching");
        boolean unaryPolicy = entry.node() instanceof cn.superhuang.data.scalpel.contract.task.GeometryDeriveNodeDefinition derive
                && derive.configuration() != null && derive.configuration().derivations() != null
                && derive.configuration().derivations().stream().anyMatch(item -> item != null && item.geometryPolicy() != null)
                || entry.node() instanceof cn.superhuang.data.scalpel.contract.task.GeometrySimplifyNodeDefinition simplify
                && simplify.configuration() != null && simplify.configuration().geometryPolicy() != null;
        if (schemaMinorVersion < 29 && unaryPolicy) entry.result().error("GEOMETRY_UNARY_POLICY_REQUIRE_SCHEMA_VERSION",
                "显式一元几何策略从 Canvas 4.29 开始支持", "configuration");
        if (schemaMinorVersion < 46 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsNodeDefinition incident
                && incident.configuration() != null && !incident.configuration().conditionWindows().isEmpty())
            entry.result().error("TRACK_INCIDENT_WINDOWS_REQUIRE_SCHEMA_VERSION", "事件窗口指标从 Canvas 4.46 开始支持", "configuration.conditionWindows");
        if (schemaMinorVersion < 45 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialPointClusterNodeDefinition cluster
                && cluster.configuration() != null && cluster.configuration().hdbscan() != null)
            entry.result().error("SPATIAL_HDBSCAN_OPTIONS_REQUIRE_SCHEMA_VERSION", "HDBSCAN 诊断配置从 Canvas 4.45 开始支持", "configuration.hdbscan");
        if (schemaMinorVersion < 44 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition reconstruct
                && reconstruct.configuration() != null && reconstruct.configuration().reconstruction() != null
                && reconstruct.configuration().reconstruction().areaGeometry() != null
                && reconstruct.configuration().reconstruction().areaGeometry().geodesicBoundary() != null)
            entry.result().error("TRACK_GEODESIC_AREA_REQUIRE_SCHEMA_VERSION", "测地面边界配置从 Canvas 4.44 开始支持", "configuration.reconstruction.areaGeometry.geodesicBoundary");
        if (schemaMinorVersion < 43 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition reconstruct
                && reconstruct.configuration() != null && reconstruct.configuration().reconstruction() != null
                && reconstruct.configuration().reconstruction().areaGeometry() != null
                && !reconstruct.configuration().reconstruction().areaGeometry().windowBindings().isEmpty())
            entry.result().error("TRACK_BUFFER_WINDOWS_REQUIRE_SCHEMA_VERSION", "轨迹缓冲窗口绑定从 Canvas 4.43 开始支持", "configuration.reconstruction.areaGeometry.windowBindings");
        if (schemaMinorVersion < 42 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition reconstruct
                && reconstruct.configuration() != null && reconstruct.configuration().reconstruction() != null
                && reconstruct.configuration().reconstruction().areaGeometry() != null)
            entry.result().error("TRACK_AREA_GEOMETRY_REQUIRE_SCHEMA_VERSION", "显式面轨迹从 Canvas 4.42 开始支持", "configuration.reconstruction.areaGeometry");
        if (schemaMinorVersion < 28 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition reconstruct
                && reconstruct.configuration() != null && reconstruct.configuration().reconstruction() != null
                && reconstruct.configuration().reconstruction().pathGeometry() != null) {
            entry.result().error("TRACK_PATH_GEOMETRY_REQUIRE_SCHEMA_VERSION", "显式轨迹路径从 Canvas 4.28 开始支持", "configuration.reconstruction.pathGeometry");
        }
        if (schemaMinorVersion < 27 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition reconstruct
                && reconstruct.configuration() != null && reconstruct.configuration().reconstruction() != null) {
            entry.result().error("TRACK_RECONSTRUCT_OPTIONS_REQUIRE_SCHEMA_VERSION", "显式轨迹重建次序与拆分从 Canvas 4.27 开始支持", "configuration.reconstruction");
        }
        if (schemaMinorVersion < 26 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialOverlayNodeDefinition overlay
                && overlay.configuration() != null && overlay.configuration().requiresFamilyGeometryVersion()) {
            entry.result().error("SPATIAL_OVERLAY_FAMILY_REQUIRE_SCHEMA_VERSION", "五模式及显式几何输出从 Canvas 4.26 开始支持", "configuration.geometryPolicy");
        }
        if (schemaMinorVersion < 25 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition within
                && within.configuration() != null && within.configuration().groupResult() != null) {
            entry.result().error("SPATIAL_WITHIN_GROUP_RESULT_REQUIRE_SCHEMA_VERSION", "区域关联分组结果从 Canvas 4.25 开始支持", "configuration.groupResult");
        }
        if (schemaMinorVersion < 24 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition within
                && within.configuration() != null && within.configuration().usesExplicitStatistics()) {
            entry.result().error("SPATIAL_WITHIN_STATISTICS_REQUIRE_SCHEMA_VERSION", "显式区域统计从 Canvas 4.24 开始支持", "configuration.statistics");
        }
        if (schemaMinorVersion < 23 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.TrackMotionStatisticsNodeDefinition motion
                && motion.configuration() != null && (motion.configuration().motionSemantics() != null || motion.configuration().windowOptions() != null)) {
            entry.result().error("TRACK_MOTION_WINDOW_REQUIRE_SCHEMA_VERSION", "运动历史窗口配置从 Canvas 4.23 开始支持", "configuration.motionSemantics");
        }
        if (schemaMinorVersion < 22 && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.TrackFindDwellNodeDefinition dwell
                && dwell.configuration() != null && (dwell.configuration().dwellSemantics() != null || dwell.configuration().rangeOptions() != null)) {
            entry.result().error("TRACK_DWELL_RANGE_REQUIRE_SCHEMA_VERSION", "驻留候选范围配置从 Canvas 4.22 开始支持", "configuration.dwellSemantics");
        }
        if (schemaMinorVersion < 21
                && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition bins
                && bins.configuration() != null && bins.configuration().binSizeSemantics() != null) {
            entry.result().error("SPATIAL_BIN_SIZE_SEMANTICS_REQUIRE_SCHEMA_VERSION",
                    "显式格网尺寸语义从 Canvas 4.21 开始支持", "configuration.binSizeSemantics");
        }
        cn.superhuang.data.scalpel.contract.task.TrackBoundaryConfiguration boundaries = switch (entry.node()) {
            case cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition n when n.configuration() != null -> n.configuration().boundaries();
            case cn.superhuang.data.scalpel.contract.task.TrackMotionStatisticsNodeDefinition n when n.configuration() != null -> n.configuration().boundaries();
            case cn.superhuang.data.scalpel.contract.task.TrackFindDwellNodeDefinition n when n.configuration() != null -> n.configuration().boundaries();
            case cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsNodeDefinition n when n.configuration() != null -> n.configuration().boundaries();
            default -> null;
        };
        if (schemaMinorVersion < 21 && boundaries != null && boundaries.fixedTimeBoundary() != null) {
            entry.result().error("TRACK_TIME_BOUNDARY_REQUIRE_SCHEMA_VERSION",
                    "固定时间边界从 Canvas 4.21 开始支持", "configuration.boundaries.fixedTimeBoundary");
        }
        if (schemaMinorVersion < 21
                && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsNodeDefinition track
                && track.configuration() != null && track.configuration().usesLifecycleOptions()) {
            entry.result().error("TRACK_INCIDENT_OPTIONS_REQUIRE_SCHEMA_VERSION",
                    "事件生命周期配置从 Canvas 4.21 开始支持", "configuration.incidentSemantics");
        }
        if (schemaMinorVersion < 5
                && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputNodeDefinition input
                && input.configuration() != null
                && (input.configuration().eventTimeColumn() != null
                || input.configuration().watermarkDelaySeconds() != null)) {
            entry.result().error(
                    "TDENGINE_TMQ_EVENT_TIME_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                    "TMQ 事件时间配置从 Canvas " + CanvasDefinition.CURRENT_SCHEMA_VERSION
                            + ".5 开始支持",
                    "configuration.eventTimeColumn"
            );
        }
        if (schemaMinorVersion < 4
                && entry.node() instanceof cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition input
                && input.configuration() != null
                && (input.configuration().valueFormat() != null
                || input.configuration().metadataFields() != null)) {
            entry.result().error(
                    "KAFKA_INPUT_FORMAT_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                    "Kafka Input valueFormat/metadataFields 从 Canvas "
                            + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".4 开始支持",
                    "configuration.valueFormat"
            );
        }
        if (!(entry.node() instanceof cn.superhuang.data.scalpel.contract.task.TypeCastNodeDefinition typeCast)
                || typeCast.configuration() == null
                || typeCast.configuration().operations() == null) {
            return;
        }
        for (int operationIndex = 0;
             operationIndex < typeCast.configuration().operations().size();
             operationIndex++) {
            cn.superhuang.data.scalpel.contract.task.TypeCastOperation operation =
                    typeCast.configuration().operations().get(operationIndex);
            if (operation == null || operation.casts() == null) continue;
            for (int castIndex = 0; castIndex < operation.casts().size(); castIndex++) {
                cn.superhuang.data.scalpel.contract.task.ColumnTypeCast cast = operation.casts().get(castIndex);
                if (cast != null && cast.epochTimestampUnit() != null) {
                    if (schemaMinorVersion < 2) {
                        entry.result().error(
                                "EPOCH_TIMESTAMP_UNIT_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                                "epochTimestampUnit 从 Canvas " + CanvasDefinition.CURRENT_SCHEMA_VERSION
                                        + ".2 开始支持",
                                "configuration.operations[" + operationIndex + "].casts[" + castIndex
                                        + "].epochTimestampUnit"
                        );
                    } else if (schemaMinorVersion < 8
                            && cast.targetType() != null
                            && cast.targetType().type()
                            == cn.superhuang.data.scalpel.contract.type.PlatformDataType.LONG) {
                        entry.result().error(
                                "TEMPORAL_TO_EPOCH_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                                "DATE/TIMESTAMP 转 LONG Epoch 单位从 Canvas "
                                        + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".8 开始支持",
                                "configuration.operations[" + operationIndex + "].casts[" + castIndex
                                        + "].epochTimestampUnit"
                        );
                    }
                }
                if (cast != null && schemaMinorVersion < 3 && cast.stringTemporalParseOptions() != null) {
                    entry.result().error(
                            "STRING_TEMPORAL_PARSE_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                            "stringTemporalParseOptions 从 Canvas " + CanvasDefinition.CURRENT_SCHEMA_VERSION
                                    + ".3 开始支持",
                            "configuration.operations[" + operationIndex + "].casts[" + castIndex
                                    + "].stringTemporalParseOptions"
                    );
                }
                if (cast != null && schemaMinorVersion < 7 && cast.temporalStringFormatOptions() != null) {
                    entry.result().error(
                            "TEMPORAL_STRING_FORMAT_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                            "temporalStringFormatOptions 从 Canvas "
                                    + CanvasDefinition.CURRENT_SCHEMA_VERSION + ".7 开始支持",
                            "configuration.operations[" + operationIndex + "].casts[" + castIndex
                                    + "].temporalStringFormatOptions"
                    );
                }
            }
        }
    }

    private void validateCommonNode(Entry entry) {
        CanvasNodeDefinition node = entry.node();
        if (blank(node.id())) {
            entry.result().error("NODE_ID_REQUIRED", "节点 ID 不能为空", "id");
        } else if (!uuid(node.id())) {
            entry.result().error("INVALID_NODE_ID", "节点 ID 必须是 UUID", "id");
        }
        if (blank(node.name())) {
            entry.result().error("NODE_NAME_REQUIRED", "节点名称不能为空", "name");
        } else if (node.name().length() > 100) {
            entry.result().error("INVALID_NODE_NAME", "节点名称不能超过 100 个字符", "name");
        }
        validateLayout(node.layout(), entry.result());
        boolean missingConfiguration = switch (node) {
            case cn.superhuang.data.scalpel.contract.task.ModelInputNodeDefinition input -> input.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.JdbcInputNodeDefinition input -> input.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.JdbcIncrementalInputNodeDefinition input ->
                    input.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.JdbcQueryInputNodeDefinition input ->
                    input.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition input ->
                    input.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition input -> input.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SpatialServiceInputNodeDefinition input -> input.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition input -> input.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.TdEngineTmqInputNodeDefinition input ->
                    input.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.JoinNodeDefinition join -> join.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.GeometryConstructNodeDefinition construct ->
                    construct.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SpatialTransformNodeDefinition transform ->
                    transform.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.GeometryValidateNodeDefinition validate ->
                    validate.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.GeometryRepairNodeDefinition repair ->
                    repair.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.GeometryDeriveNodeDefinition derive ->
                    derive.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.GeometrySimplifyNodeDefinition simplify ->
                    simplify.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SpatialNearestNodeDefinition nearest ->
                    nearest.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SpatialSummarizeWithinNodeDefinition summarize ->
                    summarize.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SpatialOverlayNodeDefinition overlay ->
                    overlay.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.TrackReconstructNodeDefinition track ->
                    track.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.TrackMotionStatisticsNodeDefinition track ->
                    track.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.TrackFindDwellNodeDefinition track ->
                    track.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.TrackDetectIncidentsNodeDefinition track ->
                    track.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SpatialBinAggregateNodeDefinition aggregate ->
                    aggregate.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SpatialPointClusterNodeDefinition cluster ->
                    cluster.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SpatialCenterDispersionNodeDefinition analysis ->
                    analysis.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.GeometryBufferNodeDefinition buffer ->
                    buffer.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.GeometryExplodeNodeDefinition explode ->
                    explode.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SpatialMeasureNodeDefinition measure ->
                    measure.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.GeometrySerializeNodeDefinition serialize ->
                    serialize.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SpatialClipNodeDefinition clip ->
                    clip.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SpatialAggregateNodeDefinition aggregate ->
                    aggregate.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SpatialJoinNodeDefinition join ->
                    join.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.StreamJoinNodeDefinition join -> join.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition rename -> rename.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.FilterNodeDefinition filter ->
                    filter.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SqlTransformNodeDefinition sqlTransform ->
                    sqlTransform.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.SelectColumnsNodeDefinition selectColumns ->
                    selectColumns.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.DeriveColumnsNodeDefinition deriveColumns ->
                    deriveColumns.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.TypeCastNodeDefinition typeCast ->
                    typeCast.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.AggregateNodeDefinition aggregate ->
                    aggregate.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.UnionNodeDefinition union ->
                    union.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.DeduplicateNodeDefinition deduplicate ->
                    deduplicate.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.NullHandlingNodeDefinition nullHandling ->
                    nullHandling.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.ValueMappingNodeDefinition valueMapping ->
                    valueMapping.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.MaskFieldsNodeDefinition maskFields ->
                    maskFields.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.JsonExtractNodeDefinition jsonExtract ->
                    jsonExtract.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.WindowNodeDefinition window ->
                    window.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.TopNNodeDefinition topN ->
                    topN.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition output -> output.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.ModelSnapshotSyncOutputNodeDefinition output ->
                    output.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition output -> output.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.JdbcSnapshotSyncOutputNodeDefinition output ->
                    output.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.KafkaOutputNodeDefinition output -> output.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.FileOutputNodeDefinition output -> output.configuration() == null;
        };
        if (missingConfiguration) {
            entry.result().error("CONFIGURATION_REQUIRED", "节点配置不能为空", "configuration");
        }
    }

    private static void validateLayout(CanvasNodeLayout layout, MutableNodeCompilation result) {
        if (layout == null) {
            result.error("LAYOUT_REQUIRED", "节点布局不能为空", "layout");
            return;
        }
        finiteRange(layout.x(), -100000, 100000, "layout.x", result);
        finiteRange(layout.y(), -100000, 100000, "layout.y", result);
        finiteRange(layout.width(), 180, 1000, "layout.width", result);
        finiteRange(layout.height(), 96, 1000, "layout.height", result);
    }

    private static void finiteRange(
            Double value,
            double minimum,
            double maximum,
            String path,
            MutableNodeCompilation result
    ) {
        if (value == null || !Double.isFinite(value) || value < minimum || value > maximum) {
            result.error("INVALID_LAYOUT", "%s 必须是 %s 到 %s 之间的有限数值"
                    .formatted(path, minimum, maximum), path);
        }
    }

    private void validateNodeIds() {
        entriesById.forEach((id, indexes) -> {
            if (indexes.size() > 1) {
                indexes.forEach(index -> entries.get(index).result().error(
                        "DUPLICATE_NODE_ID", "节点 ID 重复：" + id, "id"));
            }
        });
    }

    private void initializeEdges(CanvasDefinition definition) {
        if (definition == null || definition.nodes() == null) {
            return;
        }
        if (definition.edges() == null) {
            canvasIssues.add(CompilationIssue.canvas("EDGES_REQUIRED", "Canvas edges 不能为空", "edges"));
            return;
        }
        Set<String> edgeIds = new HashSet<>();
        Set<String> directions = new LinkedHashSet<>();
        for (int edgeIndex = 0; edgeIndex < definition.edges().size(); edgeIndex++) {
            CanvasEdgeDefinition edge = definition.edges().get(edgeIndex);
            String path = "edges[" + edgeIndex + "]";
            if (edge == null) {
                canvasIssues.add(CompilationIssue.canvas("EDGE_REQUIRED", "连线不能为空", path));
                continue;
            }
            if (blank(edge.id()) || !uuid(edge.id())) {
                canvasIssues.add(CompilationIssue.canvas("INVALID_EDGE_ID", "连线 ID 必须是 UUID", path + ".id"));
            } else if (!edgeIds.add(edge.id())) {
                canvasIssues.add(CompilationIssue.canvas("DUPLICATE_EDGE_ID", "连线 ID 重复：" + edge.id(), path + ".id"));
            }
            Integer source = resolve(edge.sourceNodeId());
            Integer target = resolve(edge.targetNodeId());
            if (source == null || target == null) {
                canvasIssues.add(CompilationIssue.canvas(
                        "EDGE_ENDPOINT_NOT_FOUND", "连线引用了不存在或不唯一的节点", path));
                continue;
            }
            if (source.equals(target)) {
                entries.get(source).result().error("SELF_LOOP", "节点不能连接自身", "edges");
            }
            String direction = source + "\u0000" + target;
            if (!directions.add(direction)) {
                canvasIssues.add(CompilationIssue.canvas("DUPLICATE_EDGE", "存在重复方向连线", path));
                continue;
            }
            successors.get(source).add(target);
            predecessors.get(target).add(source);
        }
    }

    private Integer resolve(String id) {
        if (blank(id)) return null;
        List<Integer> indexes = entriesById.get(id);
        return indexes != null && indexes.size() == 1 ? indexes.getFirst() : null;
    }

    private void validateDegrees() {
        for (Entry entry : entries) {
            int incoming = predecessors.get(entry.index()).size();
            int outgoing = successors.get(entry.index()).size();
            CanvasNodeType type = entry.node().nodeType();
            boolean trialTarget = trialTargetNodeId != null
                    && trialTargetNodeId.equals(entry.node().id());
            boolean valid = switch (type) {
                case MODEL_INPUT, JDBC_INPUT, JDBC_INCREMENTAL_INPUT, JDBC_QUERY_INPUT,
                        FILE_DATASET_INPUT, HTTP_API_INPUT,
                        SPATIAL_SERVICE_INPUT, KAFKA_INPUT, TDENGINE_TMQ_INPUT ->
                        incoming == 0 && (outgoing >= 1 || trialTarget && outgoing == 0);
                case JOIN, SPATIAL_CLIP, SPATIAL_JOIN, STREAM_JOIN,
                        RENAME, FILTER, SQL_TRANSFORM, SELECT_COLUMNS, DERIVE_COLUMNS, TYPE_CAST, AGGREGATE,
                        DEDUPLICATE, NULL_HANDLING, VALUE_MAPPING, JSON_EXTRACT, WINDOW, TOP_N,
                        GEOMETRY_CONSTRUCT, SPATIAL_TRANSFORM, GEOMETRY_VALIDATE,
                        GEOMETRY_REPAIR, GEOMETRY_DERIVE, GEOMETRY_SIMPLIFY, SPATIAL_NEAREST,
                        SPATIAL_SUMMARIZE_WITHIN, SPATIAL_OVERLAY,
                        TRACK_RECONSTRUCT, TRACK_MOTION_STATISTICS, TRACK_FIND_DWELL,
                        TRACK_DETECT_INCIDENTS,
                        SPATIAL_BIN_AGGREGATE, SPATIAL_POINT_CLUSTER, SPATIAL_CENTER_DISPERSION,
                        GEOMETRY_BUFFER, GEOMETRY_EXPLODE,
                        SPATIAL_MEASURE, GEOMETRY_SERIALIZE, SPATIAL_AGGREGATE, UNION,
                        MASK_FIELDS -> incoming >= 1;
                case MODEL_OUTPUT, MODEL_SNAPSHOT_SYNC_OUTPUT,
                        JDBC_OUTPUT, JDBC_SNAPSHOT_SYNC_OUTPUT,
                        KAFKA_OUTPUT, FILE_OUTPUT -> incoming == 1 && outgoing == 0;
            };
            if (!valid) {
                String message = switch (type) {
                    case MODEL_INPUT -> "模型输入节点不能有入边，且至少需要一条出边";
                    case JDBC_INPUT -> "JDBC 输入节点不能有入边，且至少需要一条出边";
                    case JDBC_INCREMENTAL_INPUT -> "JDBC 增量输入节点不能有入边，且至少需要一条出边";
                    case JDBC_QUERY_INPUT -> "JDBC 查询输入节点不能有入边，且至少需要一条出边";
                    case FILE_DATASET_INPUT -> "文件数据集输入节点不能有入边，且至少需要一条出边";
                    case HTTP_API_INPUT -> "HTTP API 输入节点不能有入边，且至少需要一条出边";
                    case SPATIAL_SERVICE_INPUT -> "空间服务输入节点不能有入边，且至少需要一条出边";
                    case KAFKA_INPUT -> "Kafka 输入节点不能有入边，且至少需要一条出边";
                    case TDENGINE_TMQ_INPUT -> "TDengine TMQ 输入节点不能有入边，且至少需要一条出边";
                    case JOIN, SPATIAL_CLIP, SPATIAL_JOIN, STREAM_JOIN,
                            RENAME, FILTER, SQL_TRANSFORM, SELECT_COLUMNS, DERIVE_COLUMNS, TYPE_CAST, AGGREGATE,
                            DEDUPLICATE, NULL_HANDLING, VALUE_MAPPING, MASK_FIELDS, JSON_EXTRACT,
                            WINDOW, TOP_N, GEOMETRY_CONSTRUCT, SPATIAL_TRANSFORM,
                            GEOMETRY_VALIDATE, GEOMETRY_REPAIR, GEOMETRY_DERIVE,
                            GEOMETRY_SIMPLIFY, SPATIAL_NEAREST, SPATIAL_SUMMARIZE_WITHIN, SPATIAL_OVERLAY,
                            TRACK_RECONSTRUCT, TRACK_MOTION_STATISTICS, TRACK_FIND_DWELL,
                            TRACK_DETECT_INCIDENTS,
                            SPATIAL_BIN_AGGREGATE, SPATIAL_POINT_CLUSTER, SPATIAL_CENTER_DISPERSION,
                            GEOMETRY_BUFFER,
                            GEOMETRY_EXPLODE, SPATIAL_MEASURE, GEOMETRY_SERIALIZE,
                            SPATIAL_AGGREGATE, UNION -> "处理节点至少需要一条入边";
                    case MODEL_OUTPUT -> "模型输出节点必须有一条入边且不能有出边";
                    case MODEL_SNAPSHOT_SYNC_OUTPUT -> "模型快照同步输出节点必须有一条入边且不能有出边";
                    case JDBC_OUTPUT -> "JDBC 输出节点必须有一条入边且不能有出边";
                    case JDBC_SNAPSHOT_SYNC_OUTPUT -> "JDBC 快照同步输出节点必须有一条入边且不能有出边";
                    case KAFKA_OUTPUT -> "Kafka 输出节点必须有一条入边且不能有出边";
                    case FILE_OUTPUT -> "文件输出节点必须有一条入边且不能有出边";
                };
                entry.result().error("INVALID_NODE_DEGREE", message, "edges");
            }
            if (nodeOperators.require(type).category() == CanvasNodeCategory.PROCESSOR
                    && outgoing == 0 && !trialTarget) {
                entry.result().warning(
                        "UNCONSUMED_PROCESSOR_OUTPUT",
                        "处理结果未被任何下游节点使用，不会产生写入或流式查询",
                        "edges"
                );
            }
        }
    }

    private void validateExecutionModeGraph() {
        if (executionMode != CanvasExecutionMode.STREAMING) return;
        List<Integer> streamInputs = entries.stream()
                .filter(entry -> entry.node().nodeType() == CanvasNodeType.KAFKA_INPUT
                        || entry.node().nodeType() == CanvasNodeType.TDENGINE_TMQ_INPUT
                        || entry.node().nodeType() == CanvasNodeType.JDBC_INCREMENTAL_INPUT)
                .map(Entry::index)
                .toList();
        if (streamInputs.size() != 1) {
            canvasIssues.add(CompilationIssue.canvas(
                    "STREAMING_REQUIRES_SINGLE_UNBOUNDED_INPUT",
                    "实时任务必须且只能包含一个 Kafka、TDengine TMQ 或 JDBC 增量无界输入",
                    "nodes"
            ));
        }
        List<Integer> outputs = entries.stream()
                .filter(entry -> entry.node().nodeType() == CanvasNodeType.JDBC_OUTPUT
                        || entry.node().nodeType() == CanvasNodeType.MODEL_OUTPUT
                        || entry.node().nodeType() == CanvasNodeType.KAFKA_OUTPUT)
                .map(Entry::index)
                .toList();
        if (outputs.isEmpty()) {
            canvasIssues.add(CompilationIssue.canvas(
                    "STREAMING_OUTPUT_REQUIRED", "实时任务至少需要一个输出节点", "nodes"));
        }
        if (streamInputs.size() == 1) {
            Set<Integer> reachable = new HashSet<>();
            Deque<Integer> pending = new ArrayDeque<>();
            pending.add(streamInputs.getFirst());
            while (!pending.isEmpty()) {
                int current = pending.removeFirst();
                if (!reachable.add(current)) continue;
                pending.addAll(successors.get(current));
            }
            for (int output : outputs) {
                if (!reachable.contains(output)) {
                    entries.get(output).result().error(
                            "OUTPUT_NOT_REACHABLE_FROM_STREAM_INPUT",
                            "所有实时输出都必须从唯一无界输入可达",
                            "edges"
                    );
                }
            }
        }
        if (streamInputs.size() == 1
                && entries.get(streamInputs.getFirst()).node().nodeType()
                == CanvasNodeType.JDBC_INCREMENTAL_INPUT
                && outputs.size() != 1) {
            canvasIssues.add(CompilationIssue.canvas(
                    "JDBC_INCREMENTAL_REQUIRES_SINGLE_OUTPUT",
                    "JDBC 增量输入第一版必须且只能连接一个终端输出",
                    "nodes"
            ));
        }
        for (Entry entry : entries) {
            if (entry.node() instanceof cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition output
                    && output.configuration() != null) {
                for (int index = 0; index < output.configuration().writes().size(); index++) {
                    if (output.configuration().writes().get(index).writeMode()
                            == cn.superhuang.data.scalpel.contract.task.JdbcWriteMode.OVERWRITE) {
                        entry.result().error(
                                "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED",
                                "实时 JDBC_OUTPUT 不支持 OVERWRITE",
                                "configuration.writes[" + index + "].writeMode"
                        );
                    }
                }
            }
            if (entry.node() instanceof cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition output
                    && output.configuration() != null) {
                for (int index = 0; index < output.configuration().writes().size(); index++) {
                    if (output.configuration().writes().get(index).writeMode()
                            == cn.superhuang.data.scalpel.contract.task.JdbcWriteMode.OVERWRITE) {
                        entry.result().error(
                                "STREAMING_MODEL_OUTPUT_OVERWRITE_NOT_SUPPORTED",
                                "实时 MODEL_OUTPUT 不支持 OVERWRITE",
                                "configuration.writes[" + index + "].writeMode"
                        );
                    }
                }
            }
        }
    }

    private void buildTopologicalOrder() {
        int[] inDegree = new int[entries.size()];
        Deque<Integer> queue = new ArrayDeque<>();
        for (int index = 0; index < entries.size(); index++) {
            inDegree[index] = predecessors.get(index).size();
            if (inDegree[index] == 0) queue.addLast(index);
        }
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            topologicalOrder.add(current);
            for (int successor : successors.get(current)) {
                if (--inDegree[successor] == 0) queue.addLast(successor);
            }
        }
        if (topologicalOrder.size() != entries.size()) {
            canvasIssues.add(CompilationIssue.canvas("CANVAS_CYCLE", "画布存在循环依赖", "edges"));
            Set<Integer> ordered = new HashSet<>(topologicalOrder);
            for (Entry entry : entries) {
                if (!ordered.contains(entry.index())) {
                    entry.result().error("CANVAS_CYCLE", "节点处于循环依赖中", "edges");
                }
            }
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean uuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    record Entry(int index, int canvasIndex, CanvasNodeDefinition node, MutableNodeCompilation result) {
    }
}

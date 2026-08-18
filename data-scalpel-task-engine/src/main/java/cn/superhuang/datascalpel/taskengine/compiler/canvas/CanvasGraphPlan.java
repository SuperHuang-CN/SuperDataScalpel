package cn.superhuang.datascalpel.taskengine.compiler.canvas;

import cn.superhuang.data.scalpel.contract.task.CanvasDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasExecutionMode;
import cn.superhuang.data.scalpel.contract.task.CanvasEdgeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeLayout;
import cn.superhuang.data.scalpel.contract.task.CanvasNodeType;
import cn.superhuang.data.scalpel.contract.task.CompilationIssue;

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
    private final cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperatorRegistry nodeOperators =
            cn.superhuang.datascalpel.taskengine.canvas.CanvasNodeOperators.builtInRegistry();

    private CanvasGraphPlan(CanvasDefinition definition, CanvasExecutionMode executionMode) {
        this.executionMode = executionMode == null ? CanvasExecutionMode.BATCH : executionMode;
        initializeNodes(definition);
        validateNodeIds();
        initializeEdges(definition);
        validateDegrees();
        buildTopologicalOrder();
        validateExecutionModeGraph();
    }

    public static CanvasGraphPlan create(CanvasDefinition definition) {
        return new CanvasGraphPlan(definition, CanvasExecutionMode.BATCH);
    }

    public static CanvasGraphPlan create(CanvasDefinition definition, CanvasExecutionMode executionMode) {
        return new CanvasGraphPlan(definition, executionMode);
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
            if (node.nodeType() == CanvasNodeType.TDENGINE_TMQ_INPUT && schemaMinorVersion < 1) {
                entry.result().error(
                        "NODE_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                        "TDENGINE_TMQ_INPUT 从 Canvas 2.1 开始支持",
                        "type"
                );
            }
            if (node.nodeType() == CanvasNodeType.JDBC_INCREMENTAL_INPUT && schemaMinorVersion < 2) {
                entry.result().error(
                        "NODE_SCHEMA_MINOR_VERSION_NOT_SUPPORTED",
                        "JDBC_INCREMENTAL_INPUT 从 Canvas 2.2 开始支持",
                        "type"
                );
            }
            if (node instanceof cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition output
                    && output.configuration() != null
                    && output.configuration().writeMode()
                    == cn.superhuang.data.scalpel.contract.task.JdbcWriteMode.UPSERT
                    && schemaMinorVersion < 3) {
                entry.result().error(
                        "WRITE_MODE_REQUIRES_SCHEMA_VERSION",
                        "MODEL_OUTPUT UPSERT 从 Canvas 2.3 开始支持",
                        "configuration.writeMode"
                );
            }
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
            boolean valid = switch (type) {
                case MODEL_INPUT, JDBC_INPUT, JDBC_INCREMENTAL_INPUT, JDBC_QUERY_INPUT,
                        FILE_DATASET_INPUT, HTTP_API_INPUT,
                        SPATIAL_SERVICE_INPUT, KAFKA_INPUT, TDENGINE_TMQ_INPUT ->
                        incoming == 0 && outgoing >= 1;
                case JOIN, SPATIAL_CLIP, SPATIAL_JOIN, STREAM_JOIN ->
                        incoming == 2 && outgoing >= 1;
                case RENAME, FILTER, SELECT_COLUMNS, DERIVE_COLUMNS, TYPE_CAST, AGGREGATE,
                        DEDUPLICATE, NULL_HANDLING, VALUE_MAPPING, JSON_EXTRACT, WINDOW, TOP_N,
                        GEOMETRY_CONSTRUCT, SPATIAL_TRANSFORM, GEOMETRY_VALIDATE,
                        GEOMETRY_REPAIR, GEOMETRY_BUFFER, GEOMETRY_EXPLODE,
                        SPATIAL_MEASURE, GEOMETRY_SERIALIZE, SPATIAL_AGGREGATE ->
                        incoming == 1 && outgoing >= 1;
                case MASK_FIELDS -> incoming == 1 && outgoing == 1;
                case UNION -> incoming >= 1 && outgoing >= 1;
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
                    case JOIN -> "Join 节点必须有两条入边，且至少需要一条出边";
                    case GEOMETRY_CONSTRUCT -> "Geometry 构造节点必须有一条入边，且至少需要一条出边";
                    case SPATIAL_JOIN -> "空间连接节点必须有两条入边，且至少需要一条出边";
                    case SPATIAL_TRANSFORM -> "空间转换节点必须有一条入边，且至少需要一条出边";
                    case GEOMETRY_VALIDATE -> "Geometry 校验节点必须有一条入边，且至少需要一条出边";
                    case GEOMETRY_REPAIR -> "Geometry 修复节点必须有一条入边，且至少需要一条出边";
                    case GEOMETRY_BUFFER -> "Geometry Buffer 节点必须有一条入边，且至少需要一条出边";
                    case GEOMETRY_EXPLODE -> "Geometry 拆分节点必须有一条入边，且至少需要一条出边";
                    case SPATIAL_MEASURE -> "空间度量节点必须有一条入边，且至少需要一条出边";
                    case GEOMETRY_SERIALIZE -> "Geometry 序列化节点必须有一条入边，且至少需要一条出边";
                    case SPATIAL_CLIP -> "空间裁剪节点必须有两条入边，且至少需要一条出边";
                    case SPATIAL_AGGREGATE -> "空间聚合节点必须有一条入边，且至少需要一条出边";
                    case STREAM_JOIN -> "Stream Join 节点必须有两条入边，且至少需要一条出边";
                    case RENAME -> "重命名节点必须有一条入边，且至少需要一条出边";
                    case FILTER -> "筛选节点必须有一条入边，且至少需要一条出边";
                    case SELECT_COLUMNS -> "选择字段节点必须有一条入边，且至少需要一条出边";
                    case DERIVE_COLUMNS -> "派生字段节点必须有一条入边，且至少需要一条出边";
                    case TYPE_CAST -> "类型转换节点必须有一条入边，且至少需要一条出边";
                    case AGGREGATE -> "聚合节点必须有一条入边，且至少需要一条出边";
                    case UNION -> "合并数据节点至少需要一条入边和一条出边";
                    case DEDUPLICATE -> "去重节点必须有一条入边，且至少需要一条出边";
                    case NULL_HANDLING -> "空值处理节点必须有一条入边，且至少需要一条出边";
                    case VALUE_MAPPING -> "值映射节点必须有一条入边，且至少需要一条出边";
                    case MASK_FIELDS -> "字段脱敏节点必须有一条入边和一条出边";
                    case JSON_EXTRACT -> "JSON 提取节点必须有一条入边，且至少需要一条出边";
                    case WINDOW -> "窗口计算节点必须有一条入边，且至少需要一条出边";
                    case TOP_N -> "Top N 节点必须有一条入边，且至少需要一条出边";
                    case MODEL_OUTPUT -> "模型输出节点必须有一条入边且不能有出边";
                    case MODEL_SNAPSHOT_SYNC_OUTPUT -> "模型快照同步输出节点必须有一条入边且不能有出边";
                    case JDBC_OUTPUT -> "JDBC 输出节点必须有一条入边且不能有出边";
                    case JDBC_SNAPSHOT_SYNC_OUTPUT -> "JDBC 快照同步输出节点必须有一条入边且不能有出边";
                    case KAFKA_OUTPUT -> "Kafka 输出节点必须有一条入边且不能有出边";
                    case FILE_OUTPUT -> "文件输出节点必须有一条入边且不能有出边";
                };
                entry.result().error("INVALID_NODE_DEGREE", message, "edges");
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
                    && output.configuration() != null
                    && output.configuration().writeMode() != null
                    && output.configuration().writeMode()
                    == cn.superhuang.data.scalpel.contract.task.JdbcWriteMode.OVERWRITE) {
                entry.result().error(
                        "STREAMING_JDBC_OUTPUT_OVERWRITE_NOT_SUPPORTED",
                        "实时 JDBC_OUTPUT 不支持 OVERWRITE",
                        "configuration.writeMode"
                );
            }
            if (entry.node() instanceof cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition output
                    && output.configuration() != null
                    && output.configuration().writeMode() != null
                    && output.configuration().writeMode()
                    == cn.superhuang.data.scalpel.contract.task.JdbcWriteMode.OVERWRITE) {
                entry.result().error(
                        "STREAMING_MODEL_OUTPUT_OVERWRITE_NOT_SUPPORTED",
                        "实时 MODEL_OUTPUT 不支持 OVERWRITE",
                        "configuration.writeMode"
                );
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

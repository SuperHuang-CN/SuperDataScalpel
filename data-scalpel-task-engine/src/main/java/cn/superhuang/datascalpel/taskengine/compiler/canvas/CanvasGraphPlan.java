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
            if (schemaMinorVersion == CanvasDefinition.LEGACY_SCHEMA_MINOR_VERSION && isModelNode(node)) {
                entry.result().error(
                        "NODE_TYPE_REQUIRES_SCHEMA_VERSION",
                        node.nodeType() + " 从 Canvas 1.1 开始支持",
                        "type"
                );
            }
            if (schemaMinorVersion < 2 && node.nodeType() == CanvasNodeType.RENAME) {
                entry.result().error(
                        "NODE_TYPE_REQUIRES_SCHEMA_VERSION",
                        "RENAME 从 Canvas 1.2 开始支持",
                        "type"
                );
            }
            if (schemaMinorVersion < 3 && node.nodeType() == CanvasNodeType.STREAM_JOIN) {
                entry.result().error(
                        "NODE_TYPE_REQUIRES_SCHEMA_VERSION",
                        "STREAM_JOIN 从 Canvas 1.3 开始支持",
                        "type"
                );
            }
            if (schemaMinorVersion < 4 && node.nodeType() == CanvasNodeType.FILE_DATASET_INPUT) {
                entry.result().error(
                        "NODE_TYPE_REQUIRES_SCHEMA_VERSION",
                        "FILE_DATASET_INPUT 从 Canvas 1.4 开始支持",
                        "type"
                );
            }
            if (schemaMinorVersion < 5 && (node.nodeType() == CanvasNodeType.KAFKA_INPUT
                    || node.nodeType() == CanvasNodeType.KAFKA_OUTPUT)) {
                entry.result().error(
                        "NODE_TYPE_REQUIRES_SCHEMA_VERSION",
                        node.nodeType() + " 的内联 Value Schema 从 Canvas 1.5 开始支持",
                        "type"
                );
            }
            if (schemaMinorVersion < 6 && node.nodeType() == CanvasNodeType.FILE_OUTPUT) {
                entry.result().error(
                        "NODE_TYPE_REQUIRES_SCHEMA_VERSION",
                        "FILE_OUTPUT 从 Canvas 1.6 开始支持",
                        "type"
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

    private static boolean isModelNode(CanvasNodeDefinition node) {
        return node.nodeType() == CanvasNodeType.MODEL_INPUT || node.nodeType() == CanvasNodeType.MODEL_OUTPUT;
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
            case cn.superhuang.data.scalpel.contract.task.FileDatasetInputNodeDefinition input ->
                    input.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.HttpApiInputNodeDefinition input -> input.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.KafkaInputNodeDefinition input -> input.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.JoinNodeDefinition join -> join.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.StreamJoinNodeDefinition join -> join.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.RenameNodeDefinition rename -> rename.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.ModelOutputNodeDefinition output -> output.configuration() == null;
            case cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition output -> output.configuration() == null;
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
                case MODEL_INPUT, JDBC_INPUT, FILE_DATASET_INPUT, HTTP_API_INPUT, KAFKA_INPUT ->
                        incoming == 0 && outgoing >= 1;
                case JOIN, STREAM_JOIN -> incoming == 2 && outgoing >= 1;
                case RENAME -> incoming == 1 && outgoing >= 1;
                case MODEL_OUTPUT, JDBC_OUTPUT, KAFKA_OUTPUT, FILE_OUTPUT -> incoming == 1 && outgoing == 0;
            };
            if (!valid) {
                String message = switch (type) {
                    case MODEL_INPUT -> "模型输入节点不能有入边，且至少需要一条出边";
                    case JDBC_INPUT -> "JDBC 输入节点不能有入边，且至少需要一条出边";
                    case FILE_DATASET_INPUT -> "文件数据集输入节点不能有入边，且至少需要一条出边";
                    case HTTP_API_INPUT -> "HTTP API 输入节点不能有入边，且至少需要一条出边";
                    case KAFKA_INPUT -> "Kafka 输入节点不能有入边，且至少需要一条出边";
                    case JOIN -> "Join 节点必须有两条入边，且至少需要一条出边";
                    case STREAM_JOIN -> "Stream Join 节点必须有两条入边，且至少需要一条出边";
                    case RENAME -> "重命名节点必须有一条入边，且至少需要一条出边";
                    case MODEL_OUTPUT -> "模型输出节点必须有一条入边且不能有出边";
                    case JDBC_OUTPUT -> "JDBC 输出节点必须有一条入边且不能有出边";
                    case KAFKA_OUTPUT -> "Kafka 输出节点必须有一条入边且不能有出边";
                    case FILE_OUTPUT -> "文件输出节点必须有一条入边且不能有出边";
                };
                entry.result().error("INVALID_NODE_DEGREE", message, "edges");
            }
        }
    }

    private void validateExecutionModeGraph() {
        if (executionMode != CanvasExecutionMode.STREAMING) return;
        List<Integer> kafkaInputs = entries.stream()
                .filter(entry -> entry.node().nodeType() == CanvasNodeType.KAFKA_INPUT)
                .map(Entry::index)
                .toList();
        if (kafkaInputs.size() != 1) {
            canvasIssues.add(CompilationIssue.canvas(
                    "STREAMING_REQUIRES_SINGLE_KAFKA_INPUT",
                    "实时任务必须且只能包含一个 KAFKA_INPUT",
                    "nodes"
            ));
        }
        List<Integer> outputs = entries.stream()
                .filter(entry -> entry.node().nodeType() == CanvasNodeType.JDBC_OUTPUT
                        || entry.node().nodeType() == CanvasNodeType.KAFKA_OUTPUT)
                .map(Entry::index)
                .toList();
        if (outputs.isEmpty()) {
            canvasIssues.add(CompilationIssue.canvas(
                    "STREAMING_OUTPUT_REQUIRED", "实时任务至少需要一个输出节点", "nodes"));
        }
        if (kafkaInputs.size() == 1) {
            Set<Integer> reachable = new HashSet<>();
            Deque<Integer> pending = new ArrayDeque<>();
            pending.add(kafkaInputs.getFirst());
            while (!pending.isEmpty()) {
                int current = pending.removeFirst();
                if (!reachable.add(current)) continue;
                pending.addAll(successors.get(current));
            }
            for (int output : outputs) {
                if (!reachable.contains(output)) {
                    entries.get(output).result().error(
                            "OUTPUT_NOT_REACHABLE_FROM_STREAM_INPUT",
                            "所有实时输出都必须从唯一 Kafka 输入可达",
                            "edges"
                    );
                }
            }
        }
        for (Entry entry : entries) {
            if (entry.node() instanceof cn.superhuang.data.scalpel.contract.task.JdbcOutputNodeDefinition output
                    && output.configuration() != null
                    && output.configuration().writeMode() != null
                    && output.configuration().writeMode()
                    != cn.superhuang.data.scalpel.contract.task.JdbcWriteMode.APPEND) {
                entry.result().error(
                        "STREAMING_JDBC_OUTPUT_REQUIRES_APPEND",
                        "实时 JDBC_OUTPUT 只支持 APPEND",
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

package cn.superhuang.data.scalpel.business.task.domain;

import java.util.List;
import java.util.Map;

/** Business-only DAG. Layout never contains X6 runtime objects. */
public record WorkflowDefinition(int schemaVersion, Integer maxParallelism,
                                 List<Node> nodes, List<Edge> edges, Map<String, Position> layout) {
    public WorkflowDefinition {
        maxParallelism = maxParallelism == null ? 4 : maxParallelism;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        layout = layout == null ? Map.of() : Map.copyOf(layout);
    }
    public record Node(String id, String taskId) { }
    public record Edge(String source, String target) { }
    public record Position(double x, double y) { }
    public static WorkflowDefinition empty() { return new WorkflowDefinition(1, 4, List.of(), List.of(), Map.of()); }
}

package cn.superhuang.data.scalpel.business.task.domain;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;

/** Business-only DAG. Layout never contains X6 runtime objects. */
@Schema(description = "工作流的稳定有向无环图定义；布局只保存画布坐标，不包含前端 X6 对象。")
public record WorkflowDefinition(
        @Schema(description = "工作流定义协议版本；当前创建定义使用版本 1。") int schemaVersion,
        @Schema(description = "一次工作流父运行最多同时提交的就绪子节点数；省略时规范化为 4，必须大于等于 1，当前没有额外上限。该值限制工作流调度并发，不覆盖子任务自身或计算引擎并发策略。") Integer maxParallelism,
        @Schema(description = "工作流节点；每个节点引用一个可被工作流调度的任务。") List<Node> nodes,
        @Schema(description = "节点间依赖边；所有边必须构成无环图。") List<Edge> edges,
        @Schema(description = "以节点 id 为键的画布坐标；缺失坐标不影响执行语义。") Map<String, Position> layout) {
    public WorkflowDefinition {
        maxParallelism = maxParallelism == null ? 4 : maxParallelism;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        edges = edges == null ? List.of() : List.copyOf(edges);
        layout = layout == null ? Map.of() : Map.copyOf(layout);
    }
    @Schema(description = "工作流中的一个任务节点。")
    public record Node(
            @Schema(description = "定义内唯一节点 id，供依赖边和布局引用。") String id,
            @Schema(description = "要执行的子任务 UUID 字符串；保存时不校验，校验和发布时要求存在、PUBLISHED，且类型为 LOCAL_SQL、SPARK_CANVAS、SPARK_MODEL_QUALITY 或 SPARK_JAR。不能引用实时任务或另一个工作流。") String taskId) { }
    @Schema(description = "工作流节点之间的一条先后依赖。")
    public record Edge(
            @Schema(description = "上游节点 id；该节点成功后目标节点才可就绪。") String source,
            @Schema(description = "下游节点 id。") String target) { }
    @Schema(description = "工作流编辑画布中的节点位置。")
    public record Position(
            @Schema(description = "画布横坐标。") double x,
            @Schema(description = "画布纵坐标。") double y) { }
    public static WorkflowDefinition empty() { return new WorkflowDefinition(1, 4, List.of(), List.of(), Map.of()); }
}

package cn.superhuang.data.scalpel.contract.task;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;

@JsonClassDescription("任务 Canvas 的完整持久化定义；协议版本约束节点和边的结构，节点与连线 ID 只需在当前定义内稳定唯一。")
public record CanvasDefinition(
        @JsonPropertyDescription("Canvas 定义主协议版本；当前写入 4，用于选择整体节点契约，不是数据模型 Schema 版本。")
        Integer schemaVersion,
        @JsonPropertyDescription("同一 Canvas 主协议内的能力次版本；当前写入 77，缺失时按旧版 0 解析。")
        Integer schemaMinorVersion,
        @JsonPropertyDescription("Canvas 节点列表。")
        List<CanvasNodeDefinition> nodes,
        @JsonPropertyDescription("Canvas 有向边列表。")
        List<CanvasEdgeDefinition> edges
) {
    public static final int CURRENT_SCHEMA_VERSION = 4;
    public static final int CURRENT_SCHEMA_MINOR_VERSION = 77;
    public static final int LEGACY_SCHEMA_MINOR_VERSION = 0;

    public CanvasDefinition {
        nodes = nodes == null ? null : List.copyOf(nodes);
        edges = edges == null ? null : List.copyOf(edges);
    }

    public static CanvasDefinition empty() {
        return new CanvasDefinition(
                CURRENT_SCHEMA_VERSION,
                CURRENT_SCHEMA_MINOR_VERSION,
                List.of(),
                List.of()
        );
    }

    public int effectiveSchemaMinorVersion() {
        return schemaMinorVersion == null ? LEGACY_SCHEMA_MINOR_VERSION : schemaMinorVersion;
    }
}

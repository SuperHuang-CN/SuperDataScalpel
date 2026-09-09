package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record CanvasDefinition(
        Integer schemaVersion,
        Integer schemaMinorVersion,
        List<CanvasNodeDefinition> nodes,
        List<CanvasEdgeDefinition> edges
) {
    public static final int CURRENT_SCHEMA_VERSION = 4;
    public static final int CURRENT_SCHEMA_MINOR_VERSION = 47;
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

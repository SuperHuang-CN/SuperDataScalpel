package cn.superhuang.data.scalpel.contract.task;

import java.util.List;

public record CanvasDefinition(
        Integer schemaVersion,
        Integer schemaMinorVersion,
        List<CanvasNodeDefinition> nodes,
        List<CanvasEdgeDefinition> edges
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final int CURRENT_SCHEMA_MINOR_VERSION = 5;
    public static final int LEGACY_SCHEMA_MINOR_VERSION = 0;

    public int effectiveSchemaMinorVersion() {
        return schemaMinorVersion == null ? LEGACY_SCHEMA_MINOR_VERSION : schemaMinorVersion;
    }
}

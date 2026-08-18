package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.contract.task.*;

import java.time.Instant;
import java.util.UUID;

public record CanvasTaskDefinitionResponse(
        UUID taskId,
        boolean configured,
        int version,
        CanvasDefinitionLoadStatus loadStatus,
        Integer schemaVersion,
        Integer schemaMinorVersion,
        CanvasDefinition definition,
        String message,
        Instant updatedAt
) {

    public static CanvasTaskDefinitionResponse unconfigured(UUID taskId) {
        return new CanvasTaskDefinitionResponse(
                taskId,
                false,
                0,
                CanvasDefinitionLoadStatus.UNCONFIGURED,
                CanvasDefinition.CURRENT_SCHEMA_VERSION,
                CanvasDefinition.CURRENT_SCHEMA_MINOR_VERSION,
                CanvasDefinition.empty(),
                null,
                null
        );
    }

    public static CanvasTaskDefinitionResponse loaded(
            UUID taskId,
            int version,
            int schemaVersion,
            int schemaMinorVersion,
            CanvasDefinition definition,
            Instant updatedAt
    ) {
        return new CanvasTaskDefinitionResponse(
                taskId,
                true,
                version,
                CanvasDefinitionLoadStatus.LOADED,
                schemaVersion,
                schemaMinorVersion,
                definition,
                null,
                updatedAt
        );
    }

    public static CanvasTaskDefinitionResponse incompatible(
            UUID taskId,
            int version,
            int schemaVersion,
            int schemaMinorVersion,
            String message,
            Instant updatedAt
    ) {
        return new CanvasTaskDefinitionResponse(
                taskId,
                true,
                version,
                CanvasDefinitionLoadStatus.INCOMPATIBLE,
                schemaVersion,
                schemaMinorVersion,
                null,
                message,
                updatedAt
        );
    }
}

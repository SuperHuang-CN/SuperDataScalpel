package cn.superhuang.data.scalpel.business.task.web.response;

import cn.superhuang.data.scalpel.business.task.canvas.CanvasDefinition;

import java.time.Instant;
import java.util.UUID;

public record CanvasTaskDefinitionResponse(
        UUID taskId,
        boolean configured,
        int version,
        CanvasDefinition definition,
        Instant updatedAt
) {

    public static CanvasTaskDefinitionResponse unconfigured(UUID taskId) {
        return new CanvasTaskDefinitionResponse(taskId, false, 0, CanvasDefinition.empty(), null);
    }
}


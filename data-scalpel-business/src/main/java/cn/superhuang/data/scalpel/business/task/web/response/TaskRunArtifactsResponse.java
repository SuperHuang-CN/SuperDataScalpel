package cn.superhuang.data.scalpel.business.task.web.response;

import java.util.UUID;

public record TaskRunArtifactsResponse(
        UUID runId,
        TaskRunArtifactMetadataResponse result,
        TaskRunArtifactMetadataResponse log
) {
}

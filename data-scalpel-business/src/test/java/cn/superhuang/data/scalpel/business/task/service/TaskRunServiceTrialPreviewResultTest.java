package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.business.task.domain.TaskType;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskRunServiceTrialPreviewResultTest {
    private final ObjectMapper objectMapper = JsonMapper.builderWithJackson2Defaults().build();

    @Test
    void validatesTheInternalExecutionIdentityInsteadOfThePublicTaskRunId() {
        UUID publicTaskRunId = UUID.randomUUID();
        UUID executionRunId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        int attempt = 1;
        var result = objectMapper.readTree("""
                {
                  "schemaVersion": 9,
                  "taskType": "SPARK_JAR",
                  "runId": "%s",
                  "executionId": "%s",
                  "attempt": %d,
                  "trialPreview": { "writes": [] }
                }
                """.formatted(executionRunId, executionId, attempt));

        assertTrue(TaskRunArtifactQueryService.isCompatibleTrialPreviewResult(
                result, TaskType.SPARK_JAR, executionRunId, executionId, attempt));
        assertFalse(TaskRunArtifactQueryService.isCompatibleTrialPreviewResult(
                result, TaskType.SPARK_JAR, publicTaskRunId, executionId, attempt));
    }

    @Test
    void rejectsAnArtifactWhoseExecutionMetadataDoesNotMatchTheStoredRun() {
        UUID executionRunId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        var result = objectMapper.readTree("""
                {
                  "schemaVersion": 9,
                  "taskType": "SPARK_JAR",
                  "runId": "%s",
                  "executionId": "%s",
                  "attempt": 2
                }
                """.formatted(executionRunId, executionId));

        assertFalse(TaskRunArtifactQueryService.isCompatibleTrialPreviewResult(
                result, TaskType.SPARK_JAR, executionRunId, executionId, 1));
        assertFalse(TaskRunArtifactQueryService.isCompatibleTrialPreviewResult(
                result, TaskType.SPARK_CANVAS, executionRunId, executionId, 2));
    }

    @Test
    void acceptsCanvasTrialPreviewResultVersionsTenAndElevenOnly() {
        UUID executionRunId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();

        for (int schemaVersion : new int[]{10, 11}) {
            assertTrue(TaskRunArtifactQueryService.isCompatibleCanvasTrialPreviewResult(
                    canvasResult(schemaVersion, executionRunId, executionId),
                    TaskType.SPARK_CANVAS, executionRunId, executionId, 1));
        }
        for (int schemaVersion : new int[]{9, 12}) {
            assertFalse(TaskRunArtifactQueryService.isCompatibleCanvasTrialPreviewResult(
                    canvasResult(schemaVersion, executionRunId, executionId),
                    TaskType.SPARK_CANVAS, executionRunId, executionId, 1));
        }
    }

    private JsonNode canvasResult(
            int schemaVersion,
            UUID executionRunId,
            UUID executionId
    ) {
        return objectMapper.readTree("""
                {
                  "schemaVersion": %d,
                  "taskType": "SPARK_CANVAS",
                  "runId": "%s",
                  "executionId": "%s",
                  "attempt": 1
                }
                """.formatted(schemaVersion, executionRunId, executionId));
    }
}

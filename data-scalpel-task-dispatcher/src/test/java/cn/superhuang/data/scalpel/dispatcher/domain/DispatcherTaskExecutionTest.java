package cn.superhuang.data.scalpel.dispatcher.domain;

import cn.superhuang.data.scalpel.contract.execution.ExecutionArtifactLocation;
import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.StartStreamingExecutionCommand;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DispatcherTaskExecutionTest {

    @Test
    void preservesStreamingStopRequestedDuringBackendSubmission() {
        UUID runId = UUID.randomUUID();
        DispatcherTaskExecution execution = DispatcherTaskExecution.queue(
                streamingCommand(runId),
                "f".repeat(64),
                ExecutionBackendType.LOCAL_DOCKER
        );

        execution.beginSubmission();
        execution.requestStreamingStop(60);
        execution.submitted("docker-container-id", "http://spark.local/application");

        assertThat(execution.isStreamingStopRequested()).isTrue();
        assertThat(execution.getStreamingForceStopAt()).isNotNull();
        assertThat(execution.getState()).isEqualTo(DispatcherExecutionState.CANCEL_REQUESTED);
        assertThat(execution.getExternalExecutionId()).isEqualTo("docker-container-id");
    }

    private static StartStreamingExecutionCommand streamingCommand(UUID runId) {
        String prefix = "task-runs/" + runId + "/attempts/1/";
        return new StartStreamingExecutionCommand(
                1,
                UUID.randomUUID(),
                ExecutionMessageType.START_STREAMING_EXECUTION,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                runId,
                1,
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                new ExecutionArtifactLocation(
                        prefix + "manifest.json",
                        "a".repeat(64),
                        prefix + "result.json",
                        prefix + "console.log"
                )
        );
    }
}

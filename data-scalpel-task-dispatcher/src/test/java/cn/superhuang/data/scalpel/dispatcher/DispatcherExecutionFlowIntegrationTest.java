package cn.superhuang.data.scalpel.dispatcher;

import cn.superhuang.data.scalpel.contract.execution.ExecutionArtifactLocation;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionTaskType;
import cn.superhuang.data.scalpel.contract.execution.StopStreamingExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.SubmitExecutionCommand;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherExecutionState;
import cn.superhuang.data.scalpel.dispatcher.artifact.DispatcherArtifactService;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherAdmissionPolicy;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherRegistrationRequest;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherRegistrationService;
import cn.superhuang.data.scalpel.dispatcher.management.DispatcherTopics;
import cn.superhuang.data.scalpel.dispatcher.messaging.MessageCoordinates;
import cn.superhuang.data.scalpel.dispatcher.messaging.command.DispatcherCommandService;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherEventOutboxRepository;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import cn.superhuang.data.scalpel.dispatcher.service.DispatcherExecutionCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class DispatcherExecutionFlowIntegrationTest {
    @Autowired DispatcherRegistrationService registrationService;
    @Autowired DispatcherCommandService commandService;
    @Autowired DispatcherTaskExecutionRepository executionRepository;
    @Autowired DispatcherEventOutboxRepository outboxRepository;
    @Autowired DispatcherExecutionCoordinator coordinator;
    @Autowired DispatcherArtifactService artifactService;

    @Test
    void deduplicatesCommandsAndCompletesFakeBackendLifecycle() throws Exception {
        UUID engineId = UUID.randomUUID();
        registrationService.activate(new DispatcherRegistrationRequest(
                1, engineId, 1,
                new DispatcherTopics("commands.flow", "runner.flow", "admin.flow"),
                new DispatcherAdmissionPolicy(20, 2, 2)
        ));
        UUID executionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        SubmitExecutionCommand first = submit(engineId, executionId, runId, UUID.randomUUID(), 1);

        assertThat(commandService.accept(first, new MessageCoordinates("commands.flow", 0, 1)))
                .isEqualTo(DispatcherCommandService.Outcome.ACCEPTED);
        assertThat(commandService.accept(first, new MessageCoordinates("commands.flow", 0, 1)))
                .isEqualTo(DispatcherCommandService.Outcome.DUPLICATE);

        SubmitExecutionCommand sameRequestNewMessage = new SubmitExecutionCommand(
                first.messageVersion(), UUID.randomUUID(), first.messageType(), first.occurredAt().plusMillis(1),
                first.engineId(), first.executionId(), first.runId(), first.attempt(), first.taskId(), first.taskType(),
                first.definitionVersion(), first.deadlineAt(), first.artifacts()
        );
        assertThat(commandService.accept(sameRequestNewMessage, new MessageCoordinates("commands.flow", 0, 2)))
                .isEqualTo(DispatcherCommandService.Outcome.DUPLICATE);

        coordinator.admit();
        assertThat(executionRepository.findByExecutionId(executionId).orElseThrow().getState())
                .isEqualTo(DispatcherExecutionState.SUBMITTED);
        coordinator.observe();
        assertThat(executionRepository.findByExecutionId(executionId).orElseThrow().getState())
                .isEqualTo(DispatcherExecutionState.RUNNING);
        Instant resultAt = Instant.now();
        artifactService.store(first.artifacts().resultKey(), ("""
                {
                  "schemaVersion":2,
                  "executionId":"%s",
                  "runId":"%s",
                  "attempt":1,
                  "state":"SUCCESS",
                  "startedAt":"%s",
                  "endedAt":"%s",
                  "durationMs":1,
                  "affectedRows":12,
                  "nodeResults":[],
                  "error":null
                }
                """).formatted(executionId, runId, resultAt.minusMillis(1), resultAt).getBytes(), "application/json");
        coordinator.observe();
        assertThat(executionRepository.findByExecutionId(executionId).orElseThrow().getState())
                .isEqualTo(DispatcherExecutionState.SUCCESS);
        assertThat(outboxRepository.findAll()).hasSize(4);
    }

    @Test
    void rejectsConflictingFingerprintWithoutReplacingLedger() {
        UUID engineId = UUID.randomUUID();
        registrationService.activate(new DispatcherRegistrationRequest(
                1, engineId, 2,
                new DispatcherTopics("commands.conflict", "runner.conflict", "admin.conflict"),
                new DispatcherAdmissionPolicy(20, 2, 2)
        ));
        UUID executionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        commandService.accept(submit(engineId, executionId, runId, taskId, 1), new MessageCoordinates("commands.conflict", 0, 1));

        assertThat(commandService.accept(
                submit(engineId, executionId, runId, taskId, 2),
                new MessageCoordinates("commands.conflict", 0, 2)
        )).isEqualTo(DispatcherCommandService.Outcome.REJECTED);
        assertThat(executionRepository.findByExecutionId(executionId).orElseThrow().getDefinitionVersion()).isEqualTo(1);
        assertThat(outboxRepository.findAll())
                .singleElement()
                .extracting(event -> event.getMessageType())
                .isEqualTo(ExecutionMessageType.EXECUTION_ACCEPTED.name());
    }

    @Test
    void expiresQueuedExecutionBeforeSubmittingItToBackend() {
        UUID engineId = UUID.randomUUID();
        registrationService.activate(new DispatcherRegistrationRequest(
                1, engineId, 3,
                new DispatcherTopics("commands.deadline", "runner.deadline", "admin.deadline"),
                new DispatcherAdmissionPolicy(20, 2, 2)
        ));
        UUID executionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        String prefix = "task-runs/" + runId + "/attempts/1/";
        Instant occurredAt = Instant.now().minusSeconds(10);
        SubmitExecutionCommand expired = new SubmitExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, occurredAt,
                engineId, executionId, runId, 1, UUID.randomUUID(), ExecutionTaskType.SPARK_CANVAS,
                1, occurredAt.plusSeconds(1), new ExecutionArtifactLocation(
                prefix + "manifest.json", "a".repeat(64), prefix + "result.json", prefix + "console.log"
        ));

        assertThat(commandService.accept(expired, new MessageCoordinates("commands.deadline", 0, 1)))
                .isEqualTo(DispatcherCommandService.Outcome.ACCEPTED);
        coordinator.admit();

        assertThat(executionRepository.findByExecutionId(executionId).orElseThrow().getState())
                .isEqualTo(DispatcherExecutionState.TIMED_OUT);
        assertThat(outboxRepository.findAll().stream().map(event -> event.getMessageType()).toList())
                .containsExactly(
                        ExecutionMessageType.EXECUTION_ACCEPTED.name(),
                        ExecutionMessageType.EXECUTION_TIMED_OUT.name());
    }

    @Test
    void stopsStreamingExecutionThatNeverReachedTheDispatcherLedger() {
        UUID engineId = UUID.randomUUID();
        registrationService.activate(new DispatcherRegistrationRequest(
                1, engineId, 4,
                new DispatcherTopics("commands.streaming-stop", "runner.streaming-stop", "admin.streaming-stop"),
                new DispatcherAdmissionPolicy(20, 2, 2)
        ));
        UUID executionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID deploymentId = UUID.randomUUID();
        StopStreamingExecutionCommand stop = new StopStreamingExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.STOP_STREAMING_EXECUTION, Instant.now(),
                engineId, executionId, runId, 1, deploymentId, "用户请求停止实时任务", 60
        );

        assertThat(commandService.accept(
                stop, new MessageCoordinates("commands.streaming-stop", 0, 1)
        )).isEqualTo(DispatcherCommandService.Outcome.ACCEPTED);
        assertThat(executionRepository.findByExecutionId(executionId)).isEmpty();
        assertThat(outboxRepository.findAll())
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.getMessageType()).isEqualTo(ExecutionMessageType.EXECUTION_STOPPED.name());
                    assertThat(event.getSequence()).isEqualTo(1);
                    assertThat(event.getExecutionId()).isEqualTo(executionId);
                });
    }

    private static SubmitExecutionCommand submit(
            UUID engineId,
            UUID executionId,
            UUID runId,
            UUID taskId,
            int definitionVersion
    ) {
        String prefix = "task-runs/" + runId + "/attempts/1/";
        Instant now = Instant.now();
        return new SubmitExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION, now,
                engineId, executionId, runId, 1, taskId, ExecutionTaskType.SPARK_CANVAS,
                definitionVersion, now.plusSeconds(3600), new ExecutionArtifactLocation(
                prefix + "manifest.json", "a".repeat(64), prefix + "result.json", prefix + "console.log"
        ));
    }
}

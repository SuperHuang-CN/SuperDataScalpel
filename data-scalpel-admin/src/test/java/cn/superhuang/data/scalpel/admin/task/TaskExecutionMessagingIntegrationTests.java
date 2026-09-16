package cn.superhuang.data.scalpel.admin.task;

import cn.superhuang.data.scalpel.business.task.domain.TaskRun;
import cn.superhuang.data.scalpel.business.task.domain.TaskRunStatus;
import cn.superhuang.data.scalpel.business.task.execution.domain.DispatcherEventInboxState;
import cn.superhuang.data.scalpel.business.task.execution.repository.DispatcherEventInboxRepository;
import cn.superhuang.data.scalpel.business.task.execution.repository.TaskExecutionOutboxRepository;
import cn.superhuang.data.scalpel.business.task.execution.service.DispatcherEventApplicationService;
import cn.superhuang.data.scalpel.business.task.execution.service.TaskExecutionOutboxService;
import cn.superhuang.data.scalpel.business.task.repository.TaskRunRepository;
import cn.superhuang.data.scalpel.contract.execution.CancelExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.ExecutionBackendType;
import cn.superhuang.data.scalpel.contract.execution.ExecutionMessageType;
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
class TaskExecutionMessagingIntegrationTests {

    @Autowired
    private TaskRunRepository runRepository;

    @Autowired
    private DispatcherEventInboxRepository inboxRepository;

    @Autowired
    private TaskExecutionOutboxRepository outboxRepository;

    @Autowired
    private DispatcherEventApplicationService eventService;

    @Autowired
    private TaskExecutionOutboxService outboxService;

    @Test
    void stopWaitsForStreamingStartToFinishRetrying() {
        Fixture fixture = fixture();
        UUID deploymentId = UUID.randomUUID();
        Instant now = Instant.now();
        String prefix = "task-runs/" + fixture.runId() + "/attempts/1/";
        var start = new cn.superhuang.data.scalpel.contract.execution.StartStreamingExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.START_STREAMING_EXECUTION, now,
                fixture.engineId(), fixture.executionId(), fixture.runId(), 1, fixture.taskId(), deploymentId, 1,
                new cn.superhuang.data.scalpel.contract.execution.ExecutionArtifactLocation(
                        prefix + "manifest.json", "a".repeat(64), prefix + "result.json", prefix + "console.log"));
        var stop = new cn.superhuang.data.scalpel.contract.execution.StopStreamingExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.STOP_STREAMING_EXECUTION, now,
                fixture.engineId(), fixture.executionId(), fixture.runId(), 1, deploymentId, "停止", 60);
        outboxService.enqueue("commands.local", start);
        outboxService.enqueue("commands.local", stop);
        var pendingStart = outboxRepository.findByMessageId(start.messageId()).orElseThrow();
        pendingStart.claim(now);
        pendingStart.failed(now, "retry");
        var pending = java.util.List.of(
                cn.superhuang.data.scalpel.business.task.execution.domain.TaskExecutionOutboxState.PENDING,
                cn.superhuang.data.scalpel.business.task.execution.domain.TaskExecutionOutboxState.FAILED);
        var page = org.springframework.data.domain.PageRequest.of(0, 50);

        assertThat(outboxRepository.findDueForUpdate(pending, now.plusMillis(100), page)).isEmpty();
        assertThat(outboxRepository.findDueForUpdate(pending, now.plusSeconds(10), page))
                .extracting(item -> item.getMessageId()).containsExactly(start.messageId());
        pendingStart.claim(now.plusSeconds(10));
        assertThat(outboxRepository.findDueForUpdate(pending, now.plusSeconds(10), page)).isEmpty();
        pendingStart.published(now.plusSeconds(10));
        assertThat(outboxRepository.findDueForUpdate(pending, now.plusSeconds(10), page))
                .extracting(item -> item.getMessageId()).containsExactly(stop.messageId());
    }

    @Test
    void appliesDispatcherEventsIdempotentlyAndIgnoresOldSequence() {
        Fixture fixture = fixture();
        TaskRun run = runRepository.save(fixture.run());

        DispatcherExecutionEvent running = fixture.event(
                run.getExecutionRunId(), ExecutionMessageType.EXECUTION_RUNNING, 2, null, Instant.now(), null
        );
        assertThat(eventService.accept(running)).isEqualTo(DispatcherEventApplicationService.Outcome.APPLIED);
        assertThat(eventService.accept(running)).isEqualTo(DispatcherEventApplicationService.Outcome.DUPLICATE);

        DispatcherExecutionEvent stale = fixture.event(
                run.getExecutionRunId(), ExecutionMessageType.EXECUTION_ACCEPTED, 1, null, null, null
        );
        assertThat(eventService.accept(stale)).isEqualTo(DispatcherEventApplicationService.Outcome.STALE);

        TaskRun reloaded = runRepository.findById(run.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(TaskRunStatus.RUNNING);
        assertThat(reloaded.getLastDispatcherEventSequence()).isEqualTo(2);
        assertThat(inboxRepository.findByMessageId(stale.messageId()).orElseThrow().getProcessingState())
                .isEqualTo(DispatcherEventInboxState.PROCESSED);
    }

    @Test
    void preservesCancelRequestedDuringLateRunningAndAcceptsFirstTerminalState() {
        Fixture fixture = fixture();
        TaskRun run = fixture.run();
        run.requestCancel();
        runRepository.save(run);

        eventService.accept(fixture.event(run.getExecutionRunId(), ExecutionMessageType.EXECUTION_RUNNING, 1, null, Instant.now(), null));
        assertThat(runRepository.findById(run.getId()).orElseThrow().getStatus()).isEqualTo(TaskRunStatus.CANCEL_REQUESTED);

        eventService.accept(fixture.event(run.getExecutionRunId(), ExecutionMessageType.EXECUTION_CANCELLED, 2, null, Instant.now(), Instant.now()));
        eventService.accept(fixture.event(run.getExecutionRunId(), ExecutionMessageType.EXECUTION_SUCCEEDED, 3, 10L, Instant.now(), Instant.now()));

        TaskRun completed = runRepository.findById(run.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskRunStatus.CANCELLED);
        assertThat(completed.getLastDispatcherEventSequence()).isEqualTo(3);
    }

    @Test
    void preservesUnknownAffectedRowsForSuccessfulExecution() {
        Fixture fixture = fixture();
        TaskRun run = runRepository.save(fixture.run());
        Instant now = Instant.now();

        eventService.accept(fixture.event(
                run.getExecutionRunId(), ExecutionMessageType.EXECUTION_SUCCEEDED,
                1, null, now.minusSeconds(1), now));

        TaskRun completed = runRepository.findById(run.getId()).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(TaskRunStatus.SUCCESS);
        assertThat(completed.getAffectedRows()).isNull();
    }

    @Test
    void rejectsIdentityMismatchAndPersistsCommandOutboxInCurrentTransaction() {
        Fixture fixture = fixture();
        TaskRun run = runRepository.save(fixture.run());
        DispatcherExecutionEvent wrongEngine = new DispatcherExecutionEvent(
                1, UUID.randomUUID(), ExecutionMessageType.EXECUTION_ACCEPTED, Instant.now(),
                UUID.randomUUID(), fixture.executionId(), run.getExecutionRunId(), 1, 1,
                ExecutionBackendType.LOCAL_DOCKER, null, null, null, null, null, null
        );
        assertThat(eventService.accept(wrongEngine)).isEqualTo(DispatcherEventApplicationService.Outcome.REJECTED);
        assertThat(inboxRepository.findByMessageId(wrongEngine.messageId()).orElseThrow().getProcessingState())
                .isEqualTo(DispatcherEventInboxState.REJECTED);

        CancelExecutionCommand cancel = new CancelExecutionCommand(
                1, UUID.randomUUID(), ExecutionMessageType.CANCEL_EXECUTION, Instant.now(),
                fixture.engineId(), fixture.executionId(), run.getExecutionRunId(), 1, "用户请求停止"
        );
        outboxService.enqueue("commands.local", cancel);
        assertThat(outboxRepository.findByMessageId(cancel.messageId())).isPresent();
    }

    private static Fixture fixture() {
        return new Fixture(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    }

    private record Fixture(UUID taskId, UUID executionId, UUID engineId, UUID runId) {
        TaskRun run() {
            return TaskRun.queueDispatchedCanvas(
                    runId, taskId, 1, "{\"schemaVersion\":2}", executionId, 1,
                    Instant.now().plusSeconds(3600), engineId, "commands.local"
            );
        }

        DispatcherExecutionEvent event(
                UUID runId,
                ExecutionMessageType type,
                long sequence,
                Long affectedRows,
                Instant startedAt,
                Instant endedAt
        ) {
            return new DispatcherExecutionEvent(
                    1, UUID.randomUUID(), type, Instant.now(), engineId, executionId, runId, 1,
                    sequence, ExecutionBackendType.LOCAL_DOCKER, "container-1", null,
                    startedAt, endedAt, affectedRows, null
            );
        }
    }
}

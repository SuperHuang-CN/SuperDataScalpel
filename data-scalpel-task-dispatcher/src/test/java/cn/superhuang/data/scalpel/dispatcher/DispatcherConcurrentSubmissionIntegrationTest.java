package cn.superhuang.data.scalpel.dispatcher;

import cn.superhuang.data.scalpel.contract.execution.*;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendSubmission;
import cn.superhuang.data.scalpel.dispatcher.backend.ExternalExecutionHandle;
import cn.superhuang.data.scalpel.dispatcher.domain.DispatcherTaskExecution;
import cn.superhuang.data.scalpel.dispatcher.management.*;
import cn.superhuang.data.scalpel.dispatcher.repository.DispatcherTaskExecutionRepository;
import cn.superhuang.data.scalpel.dispatcher.service.DispatcherExecutionStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:dispatcher_concurrent;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;INIT=CREATE SCHEMA IF NOT EXISTS dispatcher")
class DispatcherConcurrentSubmissionIntegrationTest {
    @Autowired DispatcherTaskExecutionRepository executions;
    @Autowired DispatcherExecutionStateService states;
    @Autowired TransactionTemplate transactions;
    @Autowired DispatcherRegistrationService registrations;

    @Test
    void submissionWaitsForConcurrentLedgerUpdateWithoutKeepingAStaleVersion() throws Exception {
        UUID executionId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID engineId = UUID.randomUUID();
        registrations.activate(new DispatcherRegistrationRequest(engineId,
                new DispatcherTopics("commands.concurrent", "runner.concurrent", "admin.concurrent"),
                new DispatcherAdmissionPolicy(20, 2, 2),
                SparkExecutionResourcePolicy.defaultsFor(ExecutionBackendType.LOCAL_DOCKER)));
        String prefix = "task-runs/" + runId + "/attempts/1/";
        var command = new SubmitExecutionCommand(1, UUID.randomUUID(), ExecutionMessageType.SUBMIT_EXECUTION,
                Instant.now(), engineId, executionId, runId, 1, UUID.randomUUID(),
                ExecutionTaskType.SPARK_CANVAS, 1, Instant.now().plusSeconds(3600),
                new ExecutionArtifactLocation(prefix + "manifest.json", "a".repeat(64),
                        prefix + "result.json", prefix + "console.log"));
        var row = DispatcherTaskExecution.queue(command, "b".repeat(64), ExecutionBackendType.LOCAL_DOCKER,
                SparkExecutionResourcePolicy.defaultsFor(ExecutionBackendType.LOCAL_DOCKER).defaults());
        row.beginSubmission();
        transactions.executeWithoutResult(status -> executions.saveAndFlush(row));
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var observation = pool.submit(() -> transactions.executeWithoutResult(status -> {
                var current = executions.findByIdForUpdate(row.getId()).orElseThrow();
                current.observationFailed();
                executions.saveAndFlush(current);
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("lock release timeout");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            var submission = pool.submit(() -> states.submitted(executionId, new BackendSubmission(
                    new ExternalExecutionHandle(ExecutionBackendType.LOCAL_DOCKER, "container-qa", null))));
            try {
                Thread.sleep(250);
                assertThat(submission.isDone()).isFalse();
            } finally {
                release.countDown();
            }
            observation.get(10, TimeUnit.SECONDS);
            submission.get(10, TimeUnit.SECONDS);
            var completed = executions.findByExecutionId(executionId).orElseThrow();
            assertThat(completed.getState()).isEqualTo(DispatcherExecutionState.SUBMITTED);
            assertThat(completed.getExternalExecutionId()).isEqualTo("container-qa");
            assertThat(completed.getObservationFailureSince()).isNotNull();
        } finally {
            release.countDown();
        }
    }
}

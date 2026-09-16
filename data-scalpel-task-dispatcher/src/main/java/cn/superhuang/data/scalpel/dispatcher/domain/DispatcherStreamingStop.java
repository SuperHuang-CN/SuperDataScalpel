package cn.superhuang.data.scalpel.dispatcher.domain;

import cn.superhuang.data.scalpel.contract.execution.StartStreamingExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.StopStreamingExecutionCommand;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/** Durable stop-before-start decision; retained across Inbox replay and process restarts. */
@Entity
@Table(name = "dispatcher_streaming_stop", uniqueConstraints =
        @UniqueConstraint(name = "uk_dispatcher_streaming_stop_attempt", columnNames = {"execution_id", "attempt"}))
public class DispatcherStreamingStop extends DispatcherBaseEntity {
    @Column(name = "execution_id", nullable = false, updatable = false)
    private UUID executionId;
    @Column(nullable = false, updatable = false)
    private int attempt;
    @Column(name = "engine_id", nullable = false, updatable = false)
    private UUID engineId;
    @Column(name = "run_id", nullable = false, updatable = false)
    private UUID runId;
    @Column(name = "deployment_id", nullable = false, updatable = false)
    private UUID deploymentId;

    protected DispatcherStreamingStop() { }

    public static DispatcherStreamingStop from(StopStreamingExecutionCommand command) {
        var stopped = new DispatcherStreamingStop();
        stopped.executionId = command.executionId();
        stopped.attempt = command.attempt();
        stopped.engineId = command.engineId();
        stopped.runId = command.runId();
        stopped.deploymentId = command.deploymentId();
        return stopped;
    }

    public boolean matches(StartStreamingExecutionCommand command) {
        return engineId.equals(command.engineId()) && runId.equals(command.runId())
                && deploymentId.equals(command.deploymentId());
    }

    public boolean matches(StopStreamingExecutionCommand command) {
        return engineId.equals(command.engineId()) && runId.equals(command.runId())
                && deploymentId.equals(command.deploymentId());
    }
}

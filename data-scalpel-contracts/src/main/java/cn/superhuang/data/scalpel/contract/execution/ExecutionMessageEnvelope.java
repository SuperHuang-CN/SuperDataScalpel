package cn.superhuang.data.scalpel.contract.execution;

import java.time.Instant;
import java.util.UUID;

public sealed interface ExecutionMessageEnvelope permits ExecutionCommand, RunnerExecutionEvent, DispatcherExecutionEvent {

    int CURRENT_VERSION = 1;

    int messageVersion();

    UUID messageId();

    ExecutionMessageType messageType();

    Instant occurredAt();

    UUID engineId();

    UUID executionId();

    UUID runId();

    int attempt();
}

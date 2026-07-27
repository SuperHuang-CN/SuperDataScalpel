package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "messageType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = RunnerStartedEvent.class, name = "RUNNER_STARTED"),
        @JsonSubTypes.Type(value = RunnerResultAvailableEvent.class, name = "RUNNER_RESULT_AVAILABLE"),
        @JsonSubTypes.Type(value = RunnerFailedEvent.class, name = "RUNNER_FAILED"),
        @JsonSubTypes.Type(value = RunnerStreamingStartedEvent.class, name = "RUNNER_STREAMING_STARTED"),
        @JsonSubTypes.Type(value = RunnerStreamingProgressEvent.class, name = "RUNNER_STREAMING_PROGRESS"),
        @JsonSubTypes.Type(value = RunnerStreamingStoppedEvent.class, name = "RUNNER_STREAMING_STOPPED")
})
public sealed interface RunnerExecutionEvent extends ExecutionMessageEnvelope permits RunnerStartedEvent, RunnerResultAvailableEvent,
        RunnerFailedEvent, RunnerStreamingStartedEvent, RunnerStreamingProgressEvent, RunnerStreamingStoppedEvent {
}

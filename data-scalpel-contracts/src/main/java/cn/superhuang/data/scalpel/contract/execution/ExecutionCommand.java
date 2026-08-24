package cn.superhuang.data.scalpel.contract.execution;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "messageType", visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SubmitExecutionCommand.class, name = "SUBMIT_EXECUTION"),
        @JsonSubTypes.Type(value = CancelExecutionCommand.class, name = "CANCEL_EXECUTION"),
        @JsonSubTypes.Type(value = ForceTerminateExecutionCommand.class, name = "FORCE_TERMINATE_EXECUTION"),
        @JsonSubTypes.Type(value = StartStreamingExecutionCommand.class, name = "START_STREAMING_EXECUTION"),
        @JsonSubTypes.Type(value = StopStreamingExecutionCommand.class, name = "STOP_STREAMING_EXECUTION")
})
public sealed interface ExecutionCommand extends ExecutionMessageEnvelope permits SubmitExecutionCommand, CancelExecutionCommand,
        ForceTerminateExecutionCommand, StartStreamingExecutionCommand, StopStreamingExecutionCommand {
}

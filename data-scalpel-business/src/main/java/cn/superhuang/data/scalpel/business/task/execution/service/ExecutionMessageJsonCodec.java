package cn.superhuang.data.scalpel.business.task.execution.service;

import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.ExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.RunnerExecutionEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Component
public class ExecutionMessageJsonCodec {

    private final ObjectMapper strictMapper;

    public ExecutionMessageJsonCodec(ObjectMapper objectMapper) {
        this.strictMapper = objectMapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    public String write(Object message) {
        return strictMapper.writeValueAsString(message);
    }

    public ExecutionCommand readCommand(String payload) {
        return strictMapper.readValue(payload, ExecutionCommand.class);
    }

    public RunnerExecutionEvent readRunnerEvent(String payload) {
        return strictMapper.readValue(payload, RunnerExecutionEvent.class);
    }

    public DispatcherExecutionEvent readDispatcherEvent(String payload) {
        return strictMapper.readValue(payload, DispatcherExecutionEvent.class);
    }
}

package cn.superhuang.data.scalpel.dispatcher.messaging;

import cn.superhuang.data.scalpel.contract.execution.DispatcherExecutionEvent;
import cn.superhuang.data.scalpel.contract.execution.ExecutionCommand;
import cn.superhuang.data.scalpel.contract.execution.RunnerExecutionEvent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Component
public class DispatcherMessageJsonCodec {
    private final ObjectMapper mapper;

    public DispatcherMessageJsonCodec(ObjectMapper objectMapper) {
        mapper = objectMapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    public String write(Object value) { return mapper.writeValueAsString(value); }
    public ExecutionCommand readCommand(String value) { return mapper.readValue(value, ExecutionCommand.class); }
    public RunnerExecutionEvent readRunnerEvent(String value) { return mapper.readValue(value, RunnerExecutionEvent.class); }
    public DispatcherExecutionEvent readDispatcherEvent(String value) { return mapper.readValue(value, DispatcherExecutionEvent.class); }
}

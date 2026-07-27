package cn.superhuang.data.scalpel.dispatcher.artifact;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;

@Component
public class DispatcherTaskResultCodec {
    private final ObjectMapper objectMapper;

    public DispatcherTaskResultCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .build();
    }

    public DispatcherTaskResult read(byte[] content) throws BackendException {
        try {
            DispatcherTaskResult result = objectMapper.readValue(content, DispatcherTaskResult.class);
            if (result == null || result.schemaVersion() == null || result.schemaVersion() != 2) {
                throw new IllegalArgumentException("Runner result.json 版本不受支持");
            }
            return result;
        } catch (RuntimeException exception) {
            throw new BackendException("INVALID_RUNNER_RESULT", "Runner result.json 不是合法协议", exception);
        }
    }
}

package cn.superhuang.data.scalpel.contract.execution;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ExecutionKafkaHeaders {

    public static final String MESSAGE_VERSION = "datascalpel-message-version";
    public static final String MESSAGE_TYPE = "datascalpel-message-type";
    public static final String ENGINE_ID = "datascalpel-engine-id";

    private ExecutionKafkaHeaders() {
    }

    public static Map<String, String> from(ExecutionMessageEnvelope message) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(MESSAGE_VERSION, Integer.toString(message.messageVersion()));
        headers.put(MESSAGE_TYPE, message.messageType().name());
        headers.put(ENGINE_ID, message.engineId().toString());
        return Map.copyOf(headers);
    }

    public static void requireMatch(ExecutionMessageEnvelope message, Map<String, String> headers) {
        Map<String, String> expected = from(message);
        if (!expected.equals(headers)) {
            throw new IllegalArgumentException("Kafka Header 与执行消息身份不一致");
        }
    }
}

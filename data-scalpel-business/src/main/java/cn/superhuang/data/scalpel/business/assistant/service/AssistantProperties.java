package cn.superhuang.data.scalpel.business.assistant.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "data-scalpel.assistant")
public record AssistantProperties(
        String credentialKey,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration staleRunTimeout,
        Integer maxOutputTokens,
        Double temperature,
        Integer maxToolRounds,
        Integer maxToolCallsPerTurn,
        Integer maxDirectoryOperations
) {
    public AssistantProperties {
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(5) : connectTimeout;
        requestTimeout = requestTimeout == null ? Duration.ofSeconds(90) : requestTimeout;
        staleRunTimeout = staleRunTimeout == null ? Duration.ofMinutes(2) : staleRunTimeout;
        maxOutputTokens = maxOutputTokens == null || maxOutputTokens < 1 ? 4096 : maxOutputTokens;
        temperature = temperature == null ? 0.1d : temperature;
        maxToolRounds = maxToolRounds == null || maxToolRounds < 1 ? 4 : maxToolRounds;
        maxToolCallsPerTurn = maxToolCallsPerTurn == null || maxToolCallsPerTurn < 1 ? 8 : maxToolCallsPerTurn;
        maxDirectoryOperations = maxDirectoryOperations == null || maxDirectoryOperations < 1
                ? 100 : maxDirectoryOperations;
    }
}

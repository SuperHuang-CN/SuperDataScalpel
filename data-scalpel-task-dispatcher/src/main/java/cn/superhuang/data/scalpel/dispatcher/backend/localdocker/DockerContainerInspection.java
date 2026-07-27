package cn.superhuang.data.scalpel.dispatcher.backend.localdocker;

import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;

public record DockerContainerInspection(
        String id,
        String name,
        String createdAt,
        Map<String, String> labels,
        String status,
        boolean running,
        String startedAt,
        String finishedAt,
        int exitCode,
        String error
) {
    public DockerContainerInspection {
        if (id == null || !id.matches("[0-9a-fA-F]{64}") || name == null || name.isBlank()
                || status == null || status.isBlank()) {
            throw new IllegalArgumentException("Docker inspect 响应缺少稳定身份或状态");
        }
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }

    public boolean hasIdentity(ExecutionIdentity identity) {
        return "true".equals(labels.get(DockerCommandFactory.MANAGED_LABEL))
                && identity.engineId().toString().equals(labels.get(DockerCommandFactory.ENGINE_ID_LABEL))
                && identity.executionId().toString().equals(labels.get(DockerCommandFactory.EXECUTION_ID_LABEL))
                && identity.runId().toString().equals(labels.get(DockerCommandFactory.RUN_ID_LABEL))
                && Integer.toString(identity.attempt()).equals(labels.get(DockerCommandFactory.ATTEMPT_LABEL));
    }

    public Instant parsedStartedAt() { return parseDockerInstant(startedAt); }
    public Instant parsedFinishedAt() { return parseDockerInstant(finishedAt); }

    private static Instant parseDockerInstant(String value) {
        if (value == null || value.isBlank() || value.startsWith("0001-01-01")) return null;
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}

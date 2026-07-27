package cn.superhuang.data.scalpel.dispatcher.backend.kubernetes;

import cn.superhuang.data.scalpel.dispatcher.backend.BackendException;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendExecutionState;
import cn.superhuang.data.scalpel.dispatcher.backend.BackendStatus;
import cn.superhuang.data.scalpel.dispatcher.backend.ExecutionIdentity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class KubernetesPodParser {
    private final ObjectMapper objectMapper;

    public KubernetesPodParser(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    public ParsedPod parse(String json) throws BackendException {
        try {
            JsonNode root = objectMapper.readTree(json);
            String name = text(root.at("/metadata/name"));
            if (name == null) throw new IllegalArgumentException("Pod name missing");
            String phase = text(root.at("/status/phase"));
            boolean terminating = !root.at("/metadata/deletionTimestamp").isMissingNode()
                    && !root.at("/metadata/deletionTimestamp").isNull();
            Instant started = instant(text(root.at("/status/startTime")));
            Instant ended = terminatedAt(root.at("/status/containerStatuses"));
            BackendStatus status;
            if (terminating) status = value(BackendExecutionState.RUNNING, started, null, null, null);
            else status = switch (phase == null ? "" : phase) {
                case "Pending" -> value(BackendExecutionState.PENDING, started, null, null, null);
                case "Running" -> value(BackendExecutionState.RUNNING, started, null, null, null);
                case "Succeeded" -> value(BackendExecutionState.SUCCEEDED, started, ended, null, null);
                case "Failed" -> value(BackendExecutionState.FAILED, started, ended,
                        "KUBERNETES_DRIVER_FAILED", "Kubernetes Spark Driver执行失败");
                default -> value(BackendExecutionState.UNKNOWN, started, ended, null, null);
            };
            return new ParsedPod(name, status, root);
        } catch (RuntimeException exception) {
            throw new BackendException("INVALID_KUBERNETES_RESPONSE", "Kubernetes Pod响应无效", exception);
        }
    }

    public List<ParsedPod> parseList(String json) throws BackendException {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode items = root.get("items");
            if (items == null || !items.isArray()) throw new IllegalArgumentException("items missing");
            List<ParsedPod> result = new ArrayList<>();
            for (JsonNode item : items) result.add(parse(objectMapper.writeValueAsString(item)));
            return List.copyOf(result);
        } catch (BackendException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BackendException("INVALID_KUBERNETES_RESPONSE", "Kubernetes Pod列表响应无效", exception);
        }
    }

    public void requireIdentity(ParsedPod pod, ExecutionIdentity identity) throws BackendException {
        JsonNode labels = pod.raw().at("/metadata/labels");
        if (!"true".equals(text(labels.get(KubernetesNames.MANAGED)))
                || !identity.engineId().toString().equals(text(labels.get(KubernetesNames.ENGINE_ID)))
                || !identity.executionId().toString().equals(text(labels.get(KubernetesNames.EXECUTION_ID)))
                || !identity.runId().toString().equals(text(labels.get(KubernetesNames.RUN_ID)))
                || !Integer.toString(identity.attempt()).equals(text(labels.get(KubernetesNames.ATTEMPT)))) {
            throw new BackendException("EXTERNAL_EXECUTION_CONFLICT", "Kubernetes Pod执行身份不匹配");
        }
    }

    public void requireSecretIdentity(String json, ExecutionIdentity identity) throws BackendException {
        try {
            JsonNode labels = objectMapper.readTree(json).at("/metadata/labels");
            if (!"true".equals(text(labels.get(KubernetesNames.MANAGED)))
                    || !identity.engineId().toString().equals(text(labels.get(KubernetesNames.ENGINE_ID)))
                    || !identity.executionId().toString().equals(text(labels.get(KubernetesNames.EXECUTION_ID)))) {
                throw new BackendException("EXTERNAL_EXECUTION_CONFLICT", "Kubernetes Secret执行身份不匹配");
            }
        } catch (BackendException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BackendException("INVALID_KUBERNETES_RESPONSE", "Kubernetes Secret响应无效", exception);
        }
    }

    private static BackendStatus value(
            BackendExecutionState state, Instant started, Instant ended, String code, String message) {
        return new BackendStatus(state, started, ended, code, message);
    }

    private static Instant terminatedAt(JsonNode statuses) {
        if (statuses == null || !statuses.isArray()) return null;
        for (JsonNode status : statuses) {
            String value = text(status.at("/state/terminated/finishedAt"));
            Instant parsed = instant(value);
            if (parsed != null) return parsed;
        }
        return null;
    }

    private static Instant instant(String value) {
        if (value == null) return null;
        try { return Instant.parse(value); }
        catch (RuntimeException ignored) { return null; }
    }

    private static String text(JsonNode node) {
        return node == null || node.isMissingNode() || node.isNull() ? null : node.asText();
    }

    public record ParsedPod(String name, BackendStatus status, JsonNode raw) { }
}

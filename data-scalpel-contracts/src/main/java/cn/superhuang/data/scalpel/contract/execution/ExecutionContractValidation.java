package cn.superhuang.data.scalpel.contract.execution;

import java.net.URI;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

final class ExecutionContractValidation {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern ERROR_CODE = Pattern.compile("[A-Z][A-Z0-9_]{0,99}");
    private static final Pattern OBJECT_KEY = Pattern.compile("task-runs/[0-9a-fA-F-]{36}/attempts/[1-9][0-9]*/[^/]+");

    private ExecutionContractValidation() {
    }

    static void envelope(
            int version,
            UUID messageId,
            ExecutionMessageType messageType,
            Instant occurredAt,
            UUID engineId,
            UUID executionId,
            UUID runId,
            int attempt
    ) {
        if (version != ExecutionMessageEnvelope.CURRENT_VERSION || messageId == null || messageType == null
                || occurredAt == null || engineId == null || executionId == null || runId == null || attempt < 1) {
            throw new IllegalArgumentException("执行消息公共字段无效");
        }
    }

    static String required(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(label + "长度超过限制");
        }
        return normalized;
    }

    static String optional(String value, int maxLength, String label) {
        if (value == null || value.isBlank()) return null;
        return required(value, maxLength, label);
    }

    static String sha256(String value) {
        String normalized = required(value, 64, "SHA-256");
        if (!SHA_256.matcher(normalized).matches()) {
            throw new IllegalArgumentException("SHA-256 必须是 64 位小写十六进制");
        }
        return normalized;
    }

    static String objectKey(String value) {
        String normalized = required(value, 500, "制品对象 Key");
        if (!OBJECT_KEY.matcher(normalized).matches() || normalized.contains("..")) {
            throw new IllegalArgumentException("制品对象 Key 不合法");
        }
        return normalized;
    }

    static void exactArtifactKey(String actual, UUID runId, int attempt, String fileName) {
        String expected = "task-runs/" + runId + "/attempts/" + attempt + "/" + fileName;
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("制品对象 Key 与运行身份不一致");
        }
    }

    static String errorCode(String value) {
        String normalized = required(value, 100, "错误代码");
        if (!ERROR_CODE.matcher(normalized).matches()) {
            throw new IllegalArgumentException("错误代码不合法");
        }
        return normalized;
    }

    static String trackingUrl(String value) {
        String normalized = optional(value, 1000, "跟踪地址");
        if (normalized == null) return null;
        try {
            URI uri = URI.create(normalized);
            if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("跟踪地址必须是有效的 HTTP(S) URL", exception);
        }
        return normalized;
    }

    static URI httpUri(URI value, String label) {
        if (value == null || value.getHost() == null || !("http".equalsIgnoreCase(value.getScheme())
                || "https".equalsIgnoreCase(value.getScheme())) || value.getUserInfo() != null) {
            throw new IllegalArgumentException(label + "必须是无 UserInfo 的 HTTP(S) URL");
        }
        return value;
    }

    static String topic(String value) {
        String normalized = required(value, 249, "Kafka Topic");
        if (!normalized.matches("[a-zA-Z0-9._-]+") || ".".equals(normalized) || "..".equals(normalized)) {
            throw new IllegalArgumentException("Kafka Topic 不合法");
        }
        return normalized;
    }
}

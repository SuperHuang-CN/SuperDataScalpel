package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

import com.taosdata.jdbc.tmq.MapDeserializer;
import com.taosdata.jdbc.tmq.TaosConsumer;
import cn.superhuang.data.scalpel.contract.task.TdEngineTmqConsumerGroupIdentity;
import org.apache.spark.sql.util.CaseInsensitiveStringMap;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Properties;
import java.util.UUID;

record TdEngineTmqOptions(
        String dataSourceId,
        String taskId,
        String nodeId,
        String executionId,
        int attempt,
        String bootstrapServers,
        String username,
        String password,
        boolean useSsl,
        String topic,
        String startingOffsets,
        int maxOffsetsPerVGroupPerTrigger,
        String initialSourceOffset
) implements Serializable {

    @Override
    public String toString() {
        return "TdEngineTmqOptions["
                + "dataSourceId=" + dataSourceId
                + ", taskId=" + taskId
                + ", nodeId=" + nodeId
                + ", executionIdHash=" + sha256(executionId).substring(0, 12)
                + ", attempt=" + attempt
                + ", topic=" + topic
                + ", startingOffsets=" + startingOffsets
                + ", maxOffsetsPerVGroupPerTrigger=" + maxOffsetsPerVGroupPerTrigger
                + ", useSsl=" + useSsl
                + ']';
    }

    static TdEngineTmqOptions from(CaseInsensitiveStringMap options) {
        String startingOffsets = required(options, "startingOffsets").toLowerCase(java.util.Locale.ROOT);
        if (!startingOffsets.equals("earliest") && !startingOffsets.equals("latest")) {
            throw new IllegalArgumentException("startingOffsets must be earliest or latest");
        }
        int maximum;
        try {
            maximum = Integer.parseInt(required(options, "maxOffsetsPerVGroupPerTrigger"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("maxOffsetsPerVGroupPerTrigger must be an integer", exception);
        }
        if (maximum < 1 || maximum > 1_000_000) {
            throw new IllegalArgumentException("maxOffsetsPerVGroupPerTrigger must be between 1 and 1000000");
        }
        int attempt;
        try {
            attempt = Integer.parseInt(required(options, "attempt"));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("attempt must be an integer", exception);
        }
        return new TdEngineTmqOptions(
                required(options, "dataSourceId"), required(options, "taskId"),
                required(options, "nodeId"),
                required(options, "executionId"), attempt,
                required(options, "bootstrapServers"), required(options, "username"),
                required(options, "password"), Boolean.parseBoolean(options.getOrDefault("useSsl", "false")),
                required(options, "topic"), startingOffsets, maximum,
                optional(options, "initialSourceOffset")
        );
    }

    TaosConsumer<java.util.Map<String, Object>> createDriverConsumer(String groupId, String clientId)
            throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("td.connect.type", "ws");
        properties.setProperty("bootstrap.servers", bootstrapServers);
        properties.setProperty("td.connect.user", username);
        properties.setProperty("td.connect.pass", password);
        properties.setProperty("useSSL", Boolean.toString(useSsl));
        properties.setProperty("group.id", groupId);
        properties.setProperty("client.id", clientId);
        properties.setProperty("enable.auto.commit", "false");
        properties.setProperty("auto.offset.reset", startingOffsets);
        properties.setProperty("value.deserializer", MapDeserializer.class.getName());
        return new TaosConsumer<>(properties);
    }

    String groupId(String checkpointLocation) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                "(?:^|/)outputs/([0-9a-fA-F-]{36})/([0-9a-fA-F-]{36})(?:/|$)")
                .matcher(checkpointLocation == null ? "" : checkpointLocation);
        if (!matcher.find()) {
            throw new IllegalArgumentException("TMQ checkpointLocation 缺少输出节点和 writeId 身份");
        }
        try {
            return TdEngineTmqConsumerGroupIdentity.groupId(
                    UUID.fromString(taskId), UUID.fromString(nodeId),
                    UUID.fromString(matcher.group(1)), UUID.fromString(matcher.group(2))
            );
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("TMQ 消费组身份无效", exception);
        }
    }

    String clientId(String suffix) {
        return "ds-tmq-" + sha256(executionId).substring(0, 12)
                + "-" + attempt + "-" + sha256(nodeId).substring(0, 8) + "-" + suffix;
    }

    private static String required(CaseInsensitiveStringMap options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(key + " is required");
        return value;
    }

    private static String optional(CaseInsensitiveStringMap options, String key) {
        String value = options.get(key);
        return value == null || value.isBlank() ? null : value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static String attemptId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

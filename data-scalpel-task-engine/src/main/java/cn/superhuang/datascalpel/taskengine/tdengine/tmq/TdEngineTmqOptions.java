package cn.superhuang.datascalpel.taskengine.tdengine.tmq;

import com.taosdata.jdbc.tmq.MapDeserializer;
import com.taosdata.jdbc.tmq.TaosConsumer;
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
        String nodeId,
        String executionId,
        int attempt,
        String bootstrapServers,
        String username,
        String password,
        boolean useSsl,
        String topic,
        String startingOffsets,
        int maxOffsetsPerVGroupPerTrigger
) implements Serializable {

    @Override
    public String toString() {
        return "TdEngineTmqOptions["
                + "dataSourceId=" + dataSourceId
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
                required(options, "dataSourceId"), required(options, "nodeId"),
                required(options, "executionId"), attempt,
                required(options, "bootstrapServers"), required(options, "username"),
                required(options, "password"), Boolean.parseBoolean(options.getOrDefault("useSsl", "false")),
                required(options, "topic"), startingOffsets, maximum
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
        properties.setProperty("auto.offset.reset", "earliest");
        properties.setProperty("value.deserializer", MapDeserializer.class.getName());
        properties.setProperty("msg.with.table.name", "true");
        return new TaosConsumer<>(properties);
    }

    String groupId(String checkpointLocation) {
        return "datascalpel-" + sha256(dataSourceId + "\0" + nodeId + "\0" + checkpointLocation).substring(0, 40);
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

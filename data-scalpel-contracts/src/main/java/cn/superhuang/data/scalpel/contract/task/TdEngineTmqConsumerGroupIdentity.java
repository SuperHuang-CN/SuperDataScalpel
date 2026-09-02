package cn.superhuang.data.scalpel.contract.task;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Stable TMQ consumer-group identity shared by the control plane and Runner. */
public final class TdEngineTmqConsumerGroupIdentity {
    private static final String PREFIX = "datascalpel-";

    private TdEngineTmqConsumerGroupIdentity() {
    }

    public static String groupId(
            UUID taskId,
            UUID sourceNodeId,
            UUID outputNodeId,
            UUID outputWriteId
    ) {
        if (taskId == null || sourceNodeId == null || outputNodeId == null || outputWriteId == null) {
            throw new IllegalArgumentException("TMQ consumer group identity is incomplete");
        }
        String identity = taskId + "\0" + sourceNodeId + "\0" + outputNodeId + "\0" + outputWriteId;
        return PREFIX + sha256(identity).substring(0, 40);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}

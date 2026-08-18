package cn.superhuang.data.scalpel.contract.task;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Stable identity of a JDBC incremental source across Canvas definition versions. */
public final class JdbcIncrementalSourceSignature {

    private JdbcIncrementalSourceSignature() {
    }

    public static String sha256(
            JdbcIncrementalInputNodeDefinition node,
            String catalogName,
            String schemaName,
            List<CanvasColumnSchema> columns
    ) {
        if (node == null || node.configuration() == null || columns == null) {
            throw new IllegalArgumentException("JDBC incremental source signature input is required");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            JdbcIncrementalInputConfiguration configuration = node.configuration();
            update(digest, node.id());
            update(digest, configuration.dataSourceId());
            update(digest, catalogName);
            update(digest, schemaName);
            update(digest, configuration.tableName());
            update(digest, configuration.incrementalTimeColumn());
            update(digest, configuration.cursorTimeZone());
            update(digest, configuration.startPosition() == null
                    ? null : configuration.startPosition().name());
            update(digest, configuration.startTime() == null
                    ? null : configuration.startTime().toString());
            for (CanvasColumnSchema column : columns) {
                update(digest, column.name());
                update(digest, column.fieldType() == null ? null : column.fieldType().name());
                update(digest, Boolean.toString(column.nullable()));
                update(digest, value(column.length()));
                update(digest, value(column.precision()));
                update(digest, value(column.scale()));
                update(digest, column.geometry() == null ? null : column.geometry().toString());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String value(Integer value) {
        return value == null ? null : value.toString();
    }
}

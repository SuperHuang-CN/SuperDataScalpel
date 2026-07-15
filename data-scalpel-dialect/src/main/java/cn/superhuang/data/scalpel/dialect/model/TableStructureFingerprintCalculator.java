package cn.superhuang.data.scalpel.dialect.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Calculates a deterministic structure-only fingerprint for portable table definitions. */
public final class TableStructureFingerprintCalculator {

    private TableStructureFingerprintCalculator() {
    }

    public static TableStructureFingerprint calculate(TableDefinition definition) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, "definition", "table-structure-v2");
        List<TableColumnDefinition> columns = definition.columns().stream()
                .sorted(Comparator.comparing(column -> normalize(column.name())))
                .toList();
        for (TableColumnDefinition column : columns) {
            append(canonical, "column", normalize(column.name()));
            append(canonical, "type", column.type().name());
            append(canonical, "length", column.length());
            append(canonical, "precision", column.precision());
            append(canonical, "scale", column.scale());
            append(canonical, "nullable", column.nullable());
        }
        for (String primaryKeyColumn : definition.primaryKeyColumns()) {
            append(canonical, "primary-key", normalize(primaryKeyColumn));
        }
        append(canonical, "storage-engine", definition.storage().engine().name());
        for (String orderByColumn : definition.storage().orderByColumns()) {
            append(canonical, "storage-order-by", normalize(orderByColumn));
        }
        return new TableStructureFingerprint(sha256(canonical.toString()));
    }

    private static void append(StringBuilder target, String key, Object value) {
        appendToken(target, key);
        appendToken(target, value == null ? "<null>" : String.valueOf(value));
    }

    private static void appendToken(StringBuilder target, String value) {
        target.append(value.length()).append(':').append(value).append(';');
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }
}

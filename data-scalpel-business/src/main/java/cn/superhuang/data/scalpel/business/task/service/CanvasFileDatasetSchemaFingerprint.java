package cn.superhuang.data.scalpel.business.task.service;

import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Compatibility-only Manifest fingerprint; the Runner does not use it as a runtime Schema gate. */
final class CanvasFileDatasetSchemaFingerprint {

    private CanvasFileDatasetSchemaFingerprint() {
    }

    static String calculate(List<CanvasColumnSchema> columns) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (int index = 0; index < columns.size(); index++) {
                CanvasColumnSchema column = columns.get(index);
                update(digest, Integer.toString(index));
                update(digest, column.name());
                update(digest, column.fieldType().name());
                update(digest, value(column.length()));
                update(digest, value(column.precision()));
                update(digest, value(column.scale()));
                update(digest, Boolean.toString(column.nullable()));
                update(digest, column.geometry() == null ? "" : column.geometry().kind().name());
                update(digest, column.geometry() == null ? "" : column.geometry().crs().authority());
                update(digest, column.geometry() == null ? "" : Integer.toString(column.geometry().crs().code()));
                update(digest, column.geometry() == null ? "" : column.geometry().dimension().name());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }

    private static String value(Integer value) {
        return value == null ? "" : value.toString();
    }
}

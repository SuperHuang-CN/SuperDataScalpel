package cn.superhuang.data.scalpel.dialect.query;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ReadOnlyQueryFingerprint {
    private ReadOnlyQueryFingerprint() {
    }

    public static String sha256(InsertSelectQuery query) {
        if (query == null || query.sql() == null) {
            throw new IllegalArgumentException("Read-only query is required");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(query.sql().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}

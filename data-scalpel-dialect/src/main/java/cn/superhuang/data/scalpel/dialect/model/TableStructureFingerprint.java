package cn.superhuang.data.scalpel.dialect.model;

import java.util.Locale;
import java.util.Objects;

/** SHA-256 fingerprint of the logical structure represented by a {@link TableDefinition}. */
public record TableStructureFingerprint(String value) {

    public TableStructureFingerprint {
        value = Objects.requireNonNull(value, "Fingerprint value is required").toLowerCase(Locale.ROOT);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Fingerprint must be a lowercase SHA-256 hex value");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}

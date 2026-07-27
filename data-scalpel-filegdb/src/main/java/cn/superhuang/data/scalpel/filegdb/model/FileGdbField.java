package cn.superhuang.data.scalpel.filegdb.model;

import java.util.Objects;

/** Immutable field metadata in physical record order. */
public record FileGdbField(
        String name,
        String alias,
        FileGdbFieldType type,
        boolean nullable,
        long length) {

    public FileGdbField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(alias, "alias");
        Objects.requireNonNull(type, "type");
        if (length < 0) {
            throw new IllegalArgumentException("length must not be negative");
        }
    }
}

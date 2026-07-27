package cn.superhuang.data.scalpel.shapefile.model;

import java.util.Objects;

/** Immutable DBF field metadata in physical order. */
public record ShapefileField(
        String name,
        ShapefileFieldType type,
        int length,
        int decimalCount,
        boolean nullable,
        int sortOrder) {
    public ShapefileField {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(type, "type");
        if (length <= 0 || decimalCount < 0 || decimalCount > length || sortOrder < 0) {
            throw new IllegalArgumentException("invalid DBF field metadata");
        }
    }
}

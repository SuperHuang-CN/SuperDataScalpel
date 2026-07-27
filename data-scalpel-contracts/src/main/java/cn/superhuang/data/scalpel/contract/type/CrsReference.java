package cn.superhuang.data.scalpel.contract.type;

import java.util.Locale;

/** Stable authority/code CRS identity, independent from a database-local SRID or SRS ID. */
public record CrsReference(String authority, int code) {

    public CrsReference {
        if (authority == null || authority.isBlank()) {
            throw new IllegalArgumentException("CRS authority is required");
        }
        authority = authority.trim().toUpperCase(Locale.ROOT);
        if (!authority.matches("[A-Z][A-Z0-9_]{0,15}")) {
            throw new IllegalArgumentException("Invalid CRS authority: " + authority);
        }
        if (code < 1) {
            throw new IllegalArgumentException("CRS code must be positive");
        }
    }

    public static CrsReference epsg(int code) {
        return new CrsReference("EPSG", code);
    }
}

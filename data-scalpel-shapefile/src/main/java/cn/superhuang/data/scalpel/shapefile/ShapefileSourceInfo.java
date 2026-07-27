package cn.superhuang.data.scalpel.shapefile;

import java.util.Objects;

/** Safe, immutable identification of the storage source used by an open dataset. */
public record ShapefileSourceInfo(ShapefileSourceType type, String location) {
    public ShapefileSourceInfo {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(location, "location");
        if (location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
    }
}

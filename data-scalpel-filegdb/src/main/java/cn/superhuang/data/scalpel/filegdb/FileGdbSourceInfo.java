package cn.superhuang.data.scalpel.filegdb;

import java.util.Objects;

/** Safe, immutable identification of the storage source used by an open database. */
public record FileGdbSourceInfo(FileGdbSourceType type, String location) {
    public FileGdbSourceInfo {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(location, "location");
        if (location.isBlank()) {
            throw new IllegalArgumentException("location must not be blank");
        }
    }
}

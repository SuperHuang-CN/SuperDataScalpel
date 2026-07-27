package cn.superhuang.data.scalpel.filegdb.model;

import java.util.Objects;

/** One logical catalog entry backed by a physical FileGDB table. */
public record FileGdbLayer(
        String id,
        String name,
        int physicalTableId,
        FileGdbLayerType type,
        boolean systemTable) {

    public FileGdbLayer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (physicalTableId <= 0) {
            throw new IllegalArgumentException("physicalTableId must be positive");
        }
    }
}

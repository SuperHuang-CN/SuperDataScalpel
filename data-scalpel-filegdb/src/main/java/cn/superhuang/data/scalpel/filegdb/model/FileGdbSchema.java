package cn.superhuang.data.scalpel.filegdb.model;

import java.util.List;
import java.util.Objects;

/** Immutable schema for one catalog layer. */
public record FileGdbSchema(
        String layerId,
        String name,
        FileGdbLayerType layerType,
        List<FileGdbField> fields,
        FileGdbSpatialReference spatialReference) {

    public FileGdbSchema {
        Objects.requireNonNull(layerId, "layerId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(layerType, "layerType");
        fields = List.copyOf(fields);
    }

    public FileGdbField field(String fieldName) {
        return fields.stream()
                .filter(field -> field.name().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown field: " + fieldName));
    }
}

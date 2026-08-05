package cn.superhuang.data.scalpel.contract.task;

import java.util.List;
import java.util.UUID;

/** Stable, credential-free resource snapshot used by a spatial-service Canvas input. */
public record SpatialServiceResourceDefinition(
        UUID id,
        String code,
        String name,
        boolean enabled,
        SpatialServiceProtocol protocol,
        String remoteIdentifier,
        String geometryFieldName,
        Integer epsgCode,
        String objectIdFieldName,
        String wfsVersion,
        String outputFormat,
        String axisOrder,
        List<CanvasColumnSchema> columns
) {
    public SpatialServiceResourceDefinition {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}

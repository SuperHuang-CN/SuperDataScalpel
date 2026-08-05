package cn.superhuang.data.scalpel.business.datasource.service;

import cn.superhuang.data.scalpel.business.datasource.domain.SpatialServiceProtocol;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;

import java.util.List;

/** Persisted protocol metadata required to compile and safely read a spatial feature resource. */
public record SpatialFeatureDefinition(
        SpatialServiceProtocol protocol,
        String remoteIdentifier,
        String serviceTitle,
        String geometryFieldName,
        Integer epsgCode,
        String objectIdFieldName,
        String wfsVersion,
        String outputFormat,
        String axisOrder,
        List<CanvasColumnSchema> columns
) {
    public SpatialFeatureDefinition {
        columns = columns == null ? List.of() : List.copyOf(columns);
    }
}

package cn.superhuang.data.scalpel.business.datasource.web.response;

import cn.superhuang.data.scalpel.business.datasource.domain.SpatialFeatureResource;
import cn.superhuang.data.scalpel.business.datasource.domain.SpatialServiceProtocol;
import cn.superhuang.data.scalpel.business.datasource.service.SpatialFeatureDefinition;
import cn.superhuang.data.scalpel.contract.task.CanvasColumnSchema;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SpatialFeatureResourceResponse(
        UUID id,
        UUID dataSourceId,
        String code,
        String name,
        SpatialServiceProtocol protocol,
        String remoteIdentifier,
        boolean enabled,
        String serviceTitle,
        String geometryFieldName,
        Integer epsgCode,
        String objectIdFieldName,
        String wfsVersion,
        String outputFormat,
        String axisOrder,
        List<CanvasColumnSchema> columns,
        Instant createdAt,
        Instant updatedAt
) {
    public static SpatialFeatureResourceResponse from(SpatialFeatureResource resource, SpatialFeatureDefinition definition) {
        return new SpatialFeatureResourceResponse(
                resource.getId(), resource.getDataSourceId(), resource.getCode(), resource.getName(),
                resource.getProtocol(), resource.getRemoteIdentifier(), resource.isEnabled(),
                definition.serviceTitle(), definition.geometryFieldName(), definition.epsgCode(),
                definition.objectIdFieldName(), definition.wfsVersion(), definition.outputFormat(),
                definition.axisOrder(), definition.columns(), resource.getCreatedAt(), resource.getUpdatedAt()
        );
    }
}

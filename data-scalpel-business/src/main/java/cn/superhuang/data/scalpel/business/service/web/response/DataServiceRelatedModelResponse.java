package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;

import java.time.Instant;
import java.util.UUID;

/** A resolved model reference used by data-service definition, relation and lineage views. */
public record DataServiceRelatedModelResponse(
        UUID modelId,
        DataServiceRelatedModelRole role,
        int order,
        boolean resolved,
        String code,
        String name,
        DataModelStatus status,
        UUID directoryId,
        String directoryName,
        UUID warehouseLayerId,
        String warehouseLayerCode,
        String warehouseLayerName,
        UUID storageDataSourceId,
        String storageDataSourceCode,
        String storageDataSourceName,
        String catalogName,
        String schemaName,
        String physicalTableName,
        Integer schemaVersion,
        Instant updatedAt
) {
}

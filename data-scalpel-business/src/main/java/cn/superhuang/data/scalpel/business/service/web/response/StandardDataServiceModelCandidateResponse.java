package cn.superhuang.data.scalpel.business.service.web.response;

import cn.superhuang.data.scalpel.business.model.domain.DataModelStatus;

import java.time.Instant;
import java.util.UUID;

/** A pageable model projection tailored to standard-table service definition selection. */
public record StandardDataServiceModelCandidateResponse(
        UUID id,
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
        int schemaVersion,
        long fieldCount,
        boolean selectable,
        String unavailableReason,
        Instant updatedAt
) {
}
